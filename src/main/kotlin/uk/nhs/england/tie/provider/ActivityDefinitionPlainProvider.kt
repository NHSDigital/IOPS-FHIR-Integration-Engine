package uk.nhs.england.tie.provider

import ca.uhn.fhir.rest.annotation.*
import ca.uhn.fhir.rest.api.MethodOutcome
import ca.uhn.fhir.rest.api.server.RequestDetails
import ca.uhn.fhir.rest.param.DateRangeParam
import ca.uhn.fhir.rest.param.ReferenceParam
import ca.uhn.fhir.rest.param.StringParam
import ca.uhn.fhir.rest.param.TokenParam
import ca.uhn.fhir.rest.server.IResourceProvider
import ca.uhn.fhir.rest.server.exceptions.UnprocessableEntityException
import org.hl7.fhir.r4.model.*
import org.springframework.stereotype.Component
import uk.nhs.england.tie.awsProvider.AWSPatient
import uk.nhs.england.tie.interceptor.CognitoAuthInterceptor

import jakarta.servlet.http.HttpServletRequest

@Component
class ActivityDefinitionPlainProvider(var cognitoAuthInterceptor: CognitoAuthInterceptor,
                                      var awsPatient: AWSPatient)  {



    @Search(type=ActivityDefinition::class)
    fun search(
        httpRequest : HttpServletRequest,
        @OptionalParam(name = ActivityDefinition.SP_NAME) name: StringParam?,
        @OptionalParam(name = ActivityDefinition.SP_TITLE) title: StringParam?,
        @OptionalParam(name = ActivityDefinition.SP_STATUS) status: TokenParam?,
        @OptionalParam(name = ActivityDefinition.SP_IDENTIFIER)  identifier :TokenParam?,
        @OptionalParam(name = ActivityDefinition.SP_RES_ID)  resid : StringParam?
    ): Bundle? {

        var queryString:String?  = awsPatient.processQueryString(httpRequest.queryString,null)

        val resource: Resource? = cognitoAuthInterceptor.readFromUrl(httpRequest.pathInfo, queryString,"ActivityDefinition")
        if (resource != null && resource is Bundle) {
            return resource
        }

        return null
    }
}
