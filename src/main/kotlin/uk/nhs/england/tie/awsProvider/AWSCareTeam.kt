package uk.nhs.england.tie.awsProvider

import ca.uhn.fhir.context.FhirContext
import ca.uhn.fhir.rest.annotation.Delete
import ca.uhn.fhir.rest.annotation.IdParam
import ca.uhn.fhir.rest.api.MethodOutcome
import ca.uhn.fhir.rest.client.api.IGenericClient
import ca.uhn.fhir.rest.param.TokenParam
import ca.uhn.fhir.rest.server.exceptions.UnprocessableEntityException
import org.hl7.fhir.instance.model.api.IBaseBundle
import org.hl7.fhir.r4.model.*
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Component
import uk.nhs.england.tie.configuration.FHIRServerProperties
import uk.nhs.england.tie.configuration.MessageProperties
import uk.nhs.england.tie.util.FhirSystems
import jakarta.servlet.http.HttpServletRequest

@Component
class AWSCareTeam(val messageProperties: MessageProperties, val awsClient: IGenericClient,
    //sqs: AmazonSQS?,
                  @Qualifier("R4") val ctx: FhirContext,
                  val fhirServerProperties: FHIRServerProperties,
                  val awsAuditEvent: AWSAuditEvent,
                  val awsOrganization: AWSOrganization,
                  val awsPractitioner: AWSPractitioner,
                  val awsPatient: AWSPatient,
                  val awsBundleProvider: AWSBundle
) {

    private val log = LoggerFactory.getLogger("FHIRAudit")
    public fun search(identifier: TokenParam) : List<CareTeam> {
        var resources = mutableListOf<CareTeam>()
        var bundle: Bundle? = null
        var retry = 3
        while (retry > 0) {
            try {
                bundle = awsClient
                    .search<IBaseBundle>()
                    .forResource(CareTeam::class.java)
                    .where(
                        CareTeam.IDENTIFIER.exactly().code(identifier.value)
                    )
                    .returnBundle(Bundle::class.java)
                    .execute()
                 break;
            } catch (ex: Exception) {
                // do nothing
                log.error(ex.message)
                retry--
                if (retry == 0) throw ex
            }
        }
        if (bundle!=null && bundle.hasEntry()) {
            for (entry in bundle.entry) {
                if (entry.hasResource() && entry.resource is CareTeam) resources.add(entry.resource as CareTeam)
            }
        }
        return resources
    }
    fun create(newCareTeam: CareTeam, bundle: Bundle?): MethodOutcome? {

        val duplicateCheck = search(TokenParam().setValue(newCareTeam.identifierFirstRep.value))
        if (duplicateCheck.size>0) throw UnprocessableEntityException("A CareTeam with this identifier already exists.")

        var response: MethodOutcome? = null
        if (newCareTeam.hasSubject() && newCareTeam.subject.hasIdentifier()) {
            val patient = awsPatient.get(newCareTeam.subject.identifier)
            if (patient != null) awsBundleProvider.updateReference(newCareTeam.subject, patient.identifierFirstRep, patient)
        }

       if (newCareTeam.hasParticipant()) {
           for (participant in newCareTeam.participant) {
               if (participant.hasMember() && participant.member.hasIdentifier()) {
                   if (participant.member.identifier.system.equals(FhirSystems.NHS_GMC_NUMBER) ||
                       participant.member.identifier.system.equals(FhirSystems.NHS_GMC_NUMBER)
                   ) {
                       val dr = awsPractitioner.get(participant.member.identifier)
                       if (dr != null) {
                           awsBundleProvider.updateReference(participant.member, dr.identifierFirstRep, dr)
                       }
                   }
                   if (participant.member.identifier.system.equals(FhirSystems.ODS_CODE)) {
                       val org = awsOrganization.get(participant.member.identifier)
                       if (org != null) {
                           awsBundleProvider.updateReference(participant.member, org.identifierFirstRep, org)
                       }
                   }
               }
               if (participant.hasOnBehalfOf() && participant.onBehalfOf.hasIdentifier()) {
                   if (participant.onBehalfOf.identifier.system.equals(FhirSystems.ODS_CODE)) {
                       val org = awsOrganization.get(participant.onBehalfOf.identifier)
                       if (org != null) {
                           awsBundleProvider.updateReference(participant.onBehalfOf, org.identifierFirstRep, org)
                       }
                   }
               }
           }
       }
        if (newCareTeam.hasManagingOrganization()) {
            for (reference in newCareTeam.managingOrganization) {
                if (reference.hasIdentifier()) {
                    if (reference.identifier.system.equals(FhirSystems.ODS_CODE)) {
                        val org = awsOrganization.get(reference.identifier)
                        if (org != null) {
                            awsBundleProvider.updateReference(reference, org.identifierFirstRep, org)
                        }
                    }
                }
            }
        }


        var retry = 3
        while (retry > 0) {
            try {
                response = awsClient
                    .create()
                    .resource(newCareTeam)
                    .execute()
                val careTeam = response.resource as CareTeam
                val auditEvent = awsAuditEvent.createAudit(careTeam, AuditEvent.AuditEventAction.C)
                awsAuditEvent.writeAWS(auditEvent)
                break
            } catch (ex: Exception) {
                // do nothing
                log.error(ex.message)
                retry--
                if (retry == 0) throw ex
            }
        }
        return response
    }
    fun delete(theId: IdType): MethodOutcome? {
        var response: MethodOutcome? = null
        var retry = 3
        while (retry > 0) {
            try {
                response = awsClient
                    .delete()
                    .resourceById(theId)
                    .execute()

              /* TODO
                val auditEvent = awsAuditEvent.createAudit(storedQuestionnaire, AuditEvent.AuditEventAction.D)
                awsAuditEvent.writeAWS(auditEvent)
                */
                break

            } catch (ex: Exception) {
                // do nothing
                log.error(ex.message)
                retry--
                if (retry == 0) throw ex
            }
        }
        return response
    }

}
