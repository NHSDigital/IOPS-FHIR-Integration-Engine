package uk.nhs.england.tie

import ca.uhn.fhir.context.FhirContext
import ca.uhn.fhir.rest.api.EncodingEnum
import ca.uhn.fhir.rest.server.RestfulServer
import org.springframework.beans.factory.annotation.Qualifier
import uk.nhs.england.tie.provider.BinaryProvider
import uk.nhs.england.tie.provider.DocumentReferenceProvider
import uk.nhs.england.tie.configuration.FHIRServerProperties
import uk.nhs.england.tie.configuration.MessageProperties
import uk.nhs.england.tie.provider.*
import uk.nhs.england.tie.interceptor.AWSAuditEventLoggingInterceptor
import uk.nhs.england.tie.interceptor.CapabilityStatementInterceptor
import uk.nhs.england.tie.interceptor.ValidationInterceptor
import java.util.*
import javax.servlet.annotation.WebServlet

@WebServlet("/FHIR/R4/*", loadOnStartup = 1)
class FHIRR4RestfulServer(
    @Qualifier("R4") fhirContext: FhirContext,
    public val fhirServerProperties: FHIRServerProperties,
    val messageProperties: MessageProperties,
    public val processMessageProvider: ProcessMessageProvider,
    val transactionProvider: TransactionProvider,
    val patientProvider: PatientProvider,
    val patientSearchProvider: PatientSearchProvider,
    val observationSearchProvider: ObservationSearchProvider,
   // val subscriptionProvider: SubscriptionProvider,


    val communicationRequestProvider: CommunicationRequestProvider,
    val communicationProvider: CommunicationProvider,
    val communicationPlainProvider: CommunicationPlainProvider,

    val questionnaireResponseProvider: QuestionnaireResponseProvider,
    val questionnaireResponsePlainProvider: QuestionnaireResponsePlainProvider,
    private val questionnaireProvider: QuestionnaireProvider,
    private val questionnairePlainProvider: QuestionnairePlainProvider,

    val serviceRequestProvider: ServiceRequestProvider,
    val taskProvider: TaskProvider,

    val binaryProvider: BinaryProvider,
    val documentReferenceProvider: DocumentReferenceProvider,

    val careTeamProvider: CareTeamProvider,
    val careTeamPlainProvider: CareTeamPlainProvider,
    val encounterProvider: EncounterProvider,
    val episodeOfCareProvider: EpisodeOfCareProvider,

    val carePlanPlainProvider: CarePlanPlainProvider,
    val carePlanProvider: CarePlanProvider,
    val goalProvider: GoalProvider,
    val goalPlainProvider: GoalPlainProvider


) : RestfulServer(fhirContext) {

    override fun initialize() {
        super.initialize()

        TimeZone.setDefault(TimeZone.getTimeZone("UTC"))

        registerProvider(processMessageProvider)
        registerProvider(transactionProvider)

        registerProvider(patientProvider)
        registerProvider(patientSearchProvider)
        registerProvider(episodeOfCareProvider)
        registerProvider(encounterProvider)


       // registerProvider(subscriptionProvider)

        registerProvider(communicationRequestProvider)
        registerProvider(communicationProvider)
        registerProvider(communicationPlainProvider)

        registerProvider(questionnaireResponseProvider)
        registerProvider(questionnaireResponsePlainProvider)
        registerProvider(questionnaireProvider)
        registerProvider(questionnairePlainProvider)
        registerProvider(observationSearchProvider)

        registerProvider(binaryProvider)
        registerProvider(documentReferenceProvider)

        registerProvider(taskProvider)
        registerProvider(careTeamProvider)
        registerProvider(careTeamPlainProvider)

        registerProvider(carePlanProvider)
        registerProvider(carePlanPlainProvider)
        registerProvider(goalProvider)
        registerProvider(goalPlainProvider)
        registerProvider(serviceRequestProvider)

        registerInterceptor(CapabilityStatementInterceptor(this.fhirContext,fhirServerProperties))


        val awsAuditEventLoggingInterceptor =
            AWSAuditEventLoggingInterceptor(
                this.fhirContext,
                fhirServerProperties
            )
        interceptorService.registerInterceptor(awsAuditEventLoggingInterceptor)

        val validationInterceptor = ValidationInterceptor(fhirContext,messageProperties)
        interceptorService.registerInterceptor(validationInterceptor)

        isDefaultPrettyPrint = true
        defaultResponseEncoding = EncodingEnum.JSON
    }
}
