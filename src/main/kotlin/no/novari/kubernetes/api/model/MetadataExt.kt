package no.novari.kubernetes.api.model

import io.fabric8.kubernetes.api.model.ObjectMeta
import no.novari.operator.ORG_ID
import no.novari.operator.TEAM

fun ObjectMeta.getTeam(): String? = labels[TEAM]

fun ObjectMeta.orgId(): String? = labels[ORG_ID]
