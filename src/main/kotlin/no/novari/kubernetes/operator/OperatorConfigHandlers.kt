package no.novari.kubernetes.operator

import io.javaoperatorsdk.operator.Operator
import java.util.function.Consumer

fun interface OperatorConfigHandler : Consumer<OperatorConfigurationOverrider>

fun interface OperatorPostConfigHandler : Consumer<Operator>
