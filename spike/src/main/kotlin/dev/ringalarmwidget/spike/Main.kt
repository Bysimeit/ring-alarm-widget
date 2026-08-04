package dev.ringalarmwidget.spike

import dev.ringalarmwidget.core.auth.AuthClient
import dev.ringalarmwidget.core.auth.AuthResult
import dev.ringalarmwidget.core.auth.LeaseFailure
import dev.ringalarmwidget.core.auth.Session
import dev.ringalarmwidget.core.auth.SessionManager
import dev.ringalarmwidget.core.auth.TokenLease
import dev.ringalarmwidget.core.auth.TwoFactorDelivery
import dev.ringalarmwidget.core.auth.TwoFactorPrompt
import dev.ringalarmwidget.core.net.ringHttpClient
import dev.ringalarmwidget.core.panel.AlarmMode
import dev.ringalarmwidget.core.panel.Fetch
import dev.ringalarmwidget.core.panel.LocationClient
import dev.ringalarmwidget.core.panel.PanelClient
import dev.ringalarmwidget.core.panel.PanelResult
import dev.ringalarmwidget.core.panel.PanelSnapshot
import dev.ringalarmwidget.core.panel.RingLocation
import io.ktor.client.engine.cio.CIO
import kotlinx.coroutines.runBlocking
import java.io.File

private fun nowEpochSeconds(): Long = System.currentTimeMillis() / 1000

private class Spike(
    val store: FileTokenStore,
    val auth: AuthClient,
    val manager: SessionManager,
    val locations: LocationClient,
    val panel: PanelClient,
)

fun main() = runBlocking {
    val store = FileTokenStore(File("spike-session.json"))
    val http = ringHttpClient(CIO.create())
    val auth = AuthClient(http, store.hardwareId, ::nowEpochSeconds)
    val locationClient = LocationClient(http, store.hardwareId)
    val spike = Spike(
        store = store,
        auth = auth,
        manager = SessionManager(auth, store, ::nowEpochSeconds),
        locations = locationClient,
        panel = PanelClient(http, locationClient),
    )

    println()
    println("=== Spike Ring Alarm Widget ===")
    println("hardware_id : ${store.hardwareId}")
    println("session     : ${store.load()?.let { describe(it) } ?: "aucune"}")
    println("location    : ${store.locationId ?: "non choisie"}")

    try {
        loop(spike)
    } finally {
        http.close()
    }
}

private suspend fun loop(spike: Spike) {
    while (true) {
        println()
        println("--- authentification ---")
        println("1) Se connecter")
        println("2) Rafraîchir maintenant (test de rotation)")
        println("3) Demander un jeton via SessionManager")
        println("4) Effacer la session locale")
        println("--- alarme ---")
        println("5) Lister les locations")
        println("6) Lire le mode courant")
        println("7) Changer de mode")
        println("q) Quitter")

        when (ask("> ").trim()) {
            "1" -> signIn(spike)
            "2" -> rotate(spike)
            "3" -> lease(spike)
            "4" -> {
                spike.store.clear()
                println("Session effacée. Le hardware_id est conservé.")
            }

            "5" -> listLocations(spike)
            "6" -> readMode(spike)
            "7" -> changeMode(spike)
            "q", "" -> return
            else -> println("Choix inconnu.")
        }
    }
}

private suspend fun signIn(spike: Spike) {
    val email = ask("E-mail Ring : ").trim()
    val password = askSecret("Mot de passe : ")

    when (val first = spike.auth.signIn(email, password)) {
        is AuthResult.Success -> accept(first.session, spike.store)

        is AuthResult.TwoFactorRequired -> {
            announce(first.prompt)
            val code = ask("Code reçu : ").trim()
            when (val second = spike.auth.signIn(email, password, code)) {
                is AuthResult.Success -> accept(second.session, spike.store)
                else -> report(second)
            }
        }

        else -> report(first)
    }
}

private suspend fun rotate(spike: Spike) {
    val before = spike.store.load()
    if (before == null) {
        println("Aucune session locale : commencer par se connecter.")
        return
    }

    println("avant  : ${describe(before)}")
    when (val result = spike.auth.refresh(before.refreshToken)) {
        is AuthResult.Success -> {
            spike.store.save(result.session)
            println("après  : ${describe(result.session)}")
            if (result.session.refreshToken == before.refreshToken) {
                println("→ refresh token INCHANGÉ")
            } else {
                println("→ refresh token PIVOTÉ : l'ancien est probablement déjà mort.")
            }
        }

        else -> report(result)
    }
}

private suspend fun lease(spike: Spike) {
    when (val lease = spike.manager.accessToken()) {
        is TokenLease.Active -> println("jeton actif : ${fingerprint(lease.accessToken)}")
        TokenLease.SignedOut -> println("déconnecté : il faut refaire une connexion complète.")
        is TokenLease.Unavailable -> println("indisponible : ${describe(lease.cause)}")
    }
}

private suspend fun requireToken(spike: Spike): String? = when (val lease = spike.manager.accessToken()) {
    is TokenLease.Active -> lease.accessToken

    TokenLease.SignedOut -> {
        println("Déconnecté : commencer par se connecter.")
        null
    }

    is TokenLease.Unavailable -> {
        println("Indisponible : ${describe(lease.cause)}")
        null
    }
}

private suspend fun listLocations(spike: Spike) {
    val token = requireToken(spike) ?: return

    when (val result = spike.locations.locations(token)) {
        is Fetch.Failed -> println("Échec ${result.statusCode ?: "?"} : ${result.diagnostic}")
        is Fetch.Ok -> {
            if (result.value.isEmpty()) {
                println("Aucune location sur ce compte.")
                return
            }
            result.value.forEachIndexed { index, location ->
                println("  ${index + 1}) ${location.name}  [${location.id}]")
            }
            chooseLocation(spike, result.value)
        }
    }
}

private fun chooseLocation(spike: Spike, locations: List<RingLocation>) {
    val answer = ask("Laquelle utiliser ? (numéro, vide pour garder) ").trim()
    if (answer.isEmpty()) return

    val picked = answer.toIntOrNull()?.let { locations.getOrNull(it - 1) }
    if (picked == null) {
        println("Choix invalide.")
        return
    }

    spike.store.locationId = picked.id
    println("Location retenue : ${picked.name}")
}

private suspend fun readMode(spike: Spike) {
    val token = requireToken(spike) ?: return
    val locationId = spike.store.locationId
    if (locationId == null) {
        println("Aucune location choisie : passer par l'action 5.")
        return
    }

    println("Connexion au panneau…")
    report(spike.panel.readMode(token, locationId))
}

private suspend fun changeMode(spike: Spike) {
    val token = requireToken(spike) ?: return
    val locationId = spike.store.locationId
    if (locationId == null) {
        println("Aucune location choisie : passer par l'action 5.")
        return
    }

    println("  1) Désarmé   2) Domicile   3) Absent")
    val mode = when (ask("Mode voulu : ").trim()) {
        "1" -> AlarmMode.DISARMED
        "2" -> AlarmMode.HOME
        "3" -> AlarmMode.AWAY
        else -> {
            println("Choix inconnu.")
            return
        }
    }

    val started = System.currentTimeMillis()
    println("Envoi de la commande « ${mode.wireValue} »…")
    val result = spike.panel.setMode(token, locationId, mode)
    println("(${System.currentTimeMillis() - started} ms)")
    report(result)
}

private fun report(result: PanelResult) {
    val message = when (result) {
        is PanelResult.Ready -> describe(result.snapshot)
        PanelResult.NoSecurityPanel -> "Aucun panneau d'alarme sur cette location."
        PanelResult.NoUsableAsset -> "Aucune base station ni pont Beams sur cette location."

        is PanelResult.Rejected ->
            "Refusé : demandé ${result.requested.wireValue}, observé ${result.observed?.wireValue ?: "inconnu"}. " +
                "Des capteurs demandent peut-être un bypass."

        is PanelResult.TimedOut -> "Délai dépassé à l'étape « ${result.stage} »."

        is PanelResult.Unreachable ->
            "Injoignable (${result.statusCode ?: "?"}) : ${result.diagnostic}"
    }
    println(message)
}

private fun describe(snapshot: PanelSnapshot): String {
    val mode = snapshot.mode?.let { label(it) } ?: "inconnu"
    val transition = if (snapshot.transitioning) ", délai de sortie en cours" else ""
    return "Panneau « ${snapshot.name ?: snapshot.zid} » : $mode$transition"
}

private fun label(mode: AlarmMode): String = when (mode) {
    AlarmMode.DISARMED -> "Désarmé"
    AlarmMode.HOME -> "Domicile"
    AlarmMode.AWAY -> "Absent"
}

private suspend fun accept(session: Session, store: FileTokenStore) {
    store.save(session)
    println("Connecté. ${describe(session)}")
}

private fun announce(prompt: TwoFactorPrompt) {
    val channel = when (prompt.delivery) {
        TwoFactorDelivery.SMS -> "par SMS"
        TwoFactorDelivery.EMAIL -> "par e-mail"
        TwoFactorDelivery.AUTHENTICATOR -> "dans l'application d'authentification"
        TwoFactorDelivery.UNKNOWN -> "par un canal non identifié"
    }
    val destination = prompt.maskedDestination?.let { " ($it)" } ?: ""
    println("Code à deux facteurs envoyé $channel$destination.")
    prompt.retryAfterSeconds?.let { println("Nouvel envoi possible dans $it s.") }
}

private fun report(result: AuthResult) {
    val message = when (result) {
        AuthResult.InvalidCredentials -> "Identifiants refusés."
        AuthResult.InvalidTwoFactorCode -> "Code invalide ou expiré."

        is AuthResult.RateLimited ->
            "Limite atteinte (10 codes / 10 min). Réessayer dans ${result.retryAfterSeconds ?: 600} s."

        is AuthResult.Failed -> "Échec ${result.statusCode ?: "?"} : ${result.diagnostic}"
        is AuthResult.TwoFactorRequired -> "2FA redemandé de façon inattendue."
        is AuthResult.Success -> "Succès."
    }
    println(message)
}

private fun describe(failure: LeaseFailure): String = when (failure) {
    is LeaseFailure.RateLimited ->
        "limite atteinte, réessayer dans ${failure.retryAfterSeconds ?: 600} s"

    is LeaseFailure.Unreachable ->
        "service injoignable (${failure.statusCode ?: "?"}) ${failure.diagnostic}"
}

private fun describe(session: Session): String {
    val remaining = session.expiresAtEpochSeconds - nowEpochSeconds()
    return "access ${fingerprint(session.accessToken)} | refresh ${fingerprint(session.refreshToken)} | expire dans ${remaining}s"
}

private fun fingerprint(token: String): String =
    if (token.length <= 12) "***" else "${token.take(6)}…${token.takeLast(4)}"

private fun ask(prompt: String): String {
    print(prompt)
    System.out.flush()
    return readlnOrNull().orEmpty()
}

private fun askSecret(prompt: String): String {
    val console = System.console()
    if (console != null) {
        return String(console.readPassword(prompt))
    }
    print("$prompt(saisie visible) ")
    System.out.flush()
    return readlnOrNull().orEmpty()
}
