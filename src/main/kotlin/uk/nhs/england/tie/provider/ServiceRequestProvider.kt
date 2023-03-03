package uk.nhs.england.tie.provider

import ca.uhn.fhir.rest.annotation.*
import ca.uhn.fhir.rest.api.MethodOutcome
import ca.uhn.fhir.rest.api.server.RequestDetails

import ca.uhn.fhir.rest.server.IResourceProvider
import ca.uhn.fhir.rest.server.exceptions.UnprocessableEntityException
import org.hl7.fhir.r4.model.*
import org.springframework.stereotype.Component
import uk.nhs.england.tie.awsProvider.AWSServiceRequest
import uk.nhs.england.tie.interceptor.CognitoAuthInterceptor
import javax.servlet.http.HttpServletRequest

@Component
class ServiceRequestProvider(var awsServiceRequest: AWSServiceRequest,
    val cognitoAuthInterceptor: CognitoAuthInterceptor) : IResourceProvider {
    override fun getResourceType(): Class<ServiceRequest> {
        return ServiceRequest::class.java
    }

    @Create
    fun create(theRequest: HttpServletRequest, @ResourceParam serviceRequest: ServiceRequest): MethodOutcome? {

        val method = MethodOutcome().setCreated(true)
        method.resource = awsServiceRequest.createUpdate(serviceRequest,null)
        return method
    }

    @Update
    fun update(
        theRequest: HttpServletRequest,
        @ResourceParam serviceRequest: ServiceRequest,
        @IdParam theId: IdType?,
        theRequestDetails: RequestDetails?
    ): MethodOutcome? {
        if (!serviceRequest.hasIdentifier()) throw UnprocessableEntityException("ServiceRequest identifier is required")
        return cognitoAuthInterceptor.updatePost(theRequest,serviceRequest)
    }

}
