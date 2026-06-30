package no.novari.kubernetes.operator.workflow

annotation class Workflow(
    val dependents: Array<Dependent> = [],
)
