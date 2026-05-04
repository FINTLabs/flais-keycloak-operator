package no.novari.operator.workflow

annotation class Workflow(
    val dependents: Array<Dependent> = [],
)
