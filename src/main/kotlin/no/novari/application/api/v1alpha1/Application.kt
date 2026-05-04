package no.novari.application.api.v1alpha1

import io.fabric8.kubernetes.api.model.Namespaced
import io.fabric8.kubernetes.client.CustomResource
import io.fabric8.kubernetes.model.annotation.Group
import io.fabric8.kubernetes.model.annotation.Kind
import io.fabric8.kubernetes.model.annotation.Version

@Group("novari.no")
@Version("v1alpha1")
@Kind("Application")
class Application :
    CustomResource<ApplicationSpec, Void>(),
    Namespaced
