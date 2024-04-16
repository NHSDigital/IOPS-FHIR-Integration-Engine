package uk.nhs.england.tie.configuration

import ca.uhn.fhir.context.FhirContext
import ca.uhn.fhir.parser.StrictErrorHandler
import ca.uhn.fhir.rest.client.api.IGenericClient
import com.amazonaws.services.sqs.AmazonSQS
import com.amazonaws.services.sqs.AmazonSQSClientBuilder
import com.amazonaws.services.sqs.model.AmazonSQSException
import com.amazonaws.services.sqs.model.CreateQueueRequest
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.web.servlet.FilterRegistrationBean
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.web.client.RestTemplate
import org.springframework.web.cors.CorsConfiguration
import org.springframework.web.cors.UrlBasedCorsConfigurationSource
import uk.nhs.england.tie.interceptor.BasicAuthInterceptor
import uk.nhs.england.tie.interceptor.CognitoAuthInterceptor
import uk.nhs.england.tie.util.CorsFilter
import jakarta.servlet.Filter

@Configuration
open class ApplicationConfiguration(val messageProperties: MessageProperties) {
    private val logger = LoggerFactory.getLogger(MessageProperties::class.java)
    @Bean("R4")
    open fun fhirR4Context(): FhirContext {
        val fhirContext = FhirContext.forR4()
        fhirContext.setParserErrorHandler(StrictErrorHandler())
        return fhirContext
    }

    @Bean("R5")
    open fun fhirR5Context(): FhirContext {
        val fhirContext = FhirContext.forR5()
        fhirContext.setParserErrorHandler(StrictErrorHandler())
        return fhirContext
    }

    @Bean("R4B")
    open fun fhirR4BContext(): FhirContext {
        val fhirContext = FhirContext.forR5()
        fhirContext.setParserErrorHandler(StrictErrorHandler())
        return fhirContext
    }

    @Bean("STU3")
    open fun fhirSTU3Context(): FhirContext {
        val fhirContext = FhirContext.forDstu3()
        fhirContext.setParserErrorHandler(StrictErrorHandler())
        return fhirContext
    }

    @Bean
    open fun restTemplate(): RestTemplate {
        return RestTemplate()
    }

    @Bean
    fun getCognitoService(messageProperties: MessageProperties, @Qualifier("R4") ctx : FhirContext, fhirServerProperties: FHIRServerProperties): CognitoAuthInterceptor? {
        return CognitoAuthInterceptor(messageProperties, fhirServerProperties, ctx)
    }

    @Bean
    fun getAWSclient(cognitoIdpInterceptor: CognitoAuthInterceptor?, mmessageProperties: MessageProperties, @Qualifier("R4") ctx : FhirContext): IGenericClient? {
        val client: IGenericClient = ctx.newRestfulGenericClient(mmessageProperties.getCdrFhirServer())
        client.registerInterceptor(cognitoIdpInterceptor)
        return client
    }

    @Bean
    fun getBasicAuth(messageProperties: MessageProperties, fhirServerProperties: FHIRServerProperties,@Qualifier("R4") ctx : FhirContext) : BasicAuthInterceptor {
        return BasicAuthInterceptor(messageProperties, fhirServerProperties , ctx)
    }

    @Bean
    fun getSQS(): AmazonSQS {
        var sqs: AmazonSQS = AmazonSQSClientBuilder.defaultClient();
        logger.info("AWS SQS Queue "+ messageProperties.getAwsQueueName() + " configuration");
        // Don't connect if not enabled
        if (!(messageProperties.getAWSQueueEnabled() == true)) return sqs;

        val create_request : CreateQueueRequest = CreateQueueRequest(messageProperties.getAwsQueueName())
            .addAttributesEntry("DelaySeconds", "60")
            .addAttributesEntry("MessageRetentionPeriod", "86400");


        try {
            sqs.createQueue(create_request);
        } catch (e : AmazonSQSException) {
            if (!e.getErrorCode().equals("QueueAlreadyExists")) {
                throw e;
            }
            logger.info("AWS SQS Queue "+ messageProperties.getAwsQueueName() + " already exists");
        }
        return sqs;
    }
}
