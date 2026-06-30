package no.novari.keycloak

import io.fabric8.kubernetes.api.model.HasMetadata
import no.novari.kubernetes.api.model.getTeam
import no.novari.kubernetes.api.model.orgId
import java.nio.charset.StandardCharsets
import java.util.zip.CRC32

object KeycloakClientNameGenerator {
    private const val ORG_NAME_LENGTH = 10
    private const val TEAM_NAME_LENGTH = 15
    private const val APP_NAME_LENGTH = 25

    fun generate(resource: HasMetadata): String =
        generate(
            team = resource.metadata.getTeam() ?: error("Missing required label 'fintlabs.no/team'"),
            name = resource.metadata.name,
            orgId = resource.metadata.orgId(),
        )

    fun generate(
        team: String,
        name: String,
        orgId: String? = null,
    ): String {
        val hash = hashedName(team = team, name = name, orgId = orgId)
        val parts =
            buildList {
                orgId
                    ?.takeIf { it.isNotBlank() }
                    ?.let { add(shortOrgName(it)) }

                add(shortTeamName(team))
                add(shortAppName(team, name))
                add(hash)
            }

        return parts.joinToString("_")
    }

    private fun hashedName(
        team: String,
        name: String,
        orgId: String?,
    ): String {
        val crc32 = CRC32()
        val baseName = team + name + orgId.orEmpty()
        val bytes = baseName.toByteArray(StandardCharsets.UTF_8)

        crc32.update(bytes, 0, bytes.size)
        return "%08x".format(crc32.value)
    }

    private fun shortOrgName(orgId: String): String = shorten(orgId, prefix = null, suffix = "_no", maxLen = ORG_NAME_LENGTH)

    private fun shortTeamName(team: String): String = shorten(team, prefix = "team", suffix = null, maxLen = TEAM_NAME_LENGTH)

    private fun shortAppName(
        team: String,
        name: String,
    ): String = shorten(name, prefix = team, suffix = null, maxLen = APP_NAME_LENGTH)

    private fun shorten(
        input: String,
        prefix: String?,
        suffix: String?,
        maxLen: Int,
    ): String {
        var value = input

        if (prefix != null && value.startsWith(prefix) && value != prefix) {
            value = value.removePrefix(prefix)
        }

        if (suffix != null && value.endsWith(suffix) && value != suffix) {
            value = value.removeSuffix(suffix)
        }

        while (value.isNotEmpty() && value.first() == '-') {
            value = value.drop(1)
        }

        if (value.length > maxLen) {
            value = value.substring(0, maxLen)
        }

        while (value.length > 1 && value.last() == '-') {
            value = value.dropLast(1)
        }

        return value
    }
}
