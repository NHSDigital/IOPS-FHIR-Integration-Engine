package uk.nhs.england.tie.provider

import ca.uhn.fhir.context.FhirContext
import ca.uhn.fhir.rest.annotation.*
import ca.uhn.fhir.rest.api.MethodOutcome
import ca.uhn.fhir.rest.api.server.RequestDetails
import ca.uhn.fhir.rest.client.api.IGenericClient

import ca.uhn.fhir.rest.server.IResourceProvider
import ca.uhn.fhir.rest.server.exceptions.UnprocessableEntityException
import org.hl7.fhir.r4.model.*
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Component
import org.thymeleaf.TemplateEngine
import uk.nhs.england.tie.awsProvider.AWSPatient
import uk.nhs.england.tie.awsProvider.AWSDiagnosticReport
import uk.nhs.england.tie.component.FHIRDocument
import uk.nhs.england.tie.interceptor.CognitoAuthInterceptor
import java.io.OutputStream
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse

@Component
class DiagnosticReportProvider(var awsDiagnosticReport: AWSDiagnosticReport,
                               var awsPatient: AWSPatient,
                               val cognitoAuthInterceptor: CognitoAuthInterceptor,
                               val client: IGenericClient, @Qualifier("R4") val ctxFHIR : FhirContext, val templateEngine: TemplateEngine
) : IResourceProvider {
    override fun getResourceType(): Class<DiagnosticReport> {
        return DiagnosticReport::class.java
    }

    @Create
    fun create(theRequest: HttpServletRequest, @ResourceParam diagnosticReport: DiagnosticReport): MethodOutcome? {

        val method = MethodOutcome().setCreated(true)
        method.resource = awsDiagnosticReport.createUpdate(diagnosticReport,null, OperationOutcome())
        return method
    }

    @Update
    fun update(
        theRequest: HttpServletRequest,
        @ResourceParam diagnosticReport: DiagnosticReport,
        @IdParam theId: IdType?,
        theRequestDetails: RequestDetails?
    ): MethodOutcome? {
        val method = MethodOutcome().setCreated(false)
        if (!diagnosticReport.hasIdentifier()) throw UnprocessableEntityException("DiagnosticReport identifier is required")
        method.resource = awsDiagnosticReport.createUpdate(diagnosticReport,null, OperationOutcome())
        return method
    }

    @Read(type=DiagnosticReport::class)
    fun read(httpRequest : HttpServletRequest, @IdParam internalId: IdType): DiagnosticReport? {
        val resource: Resource? = cognitoAuthInterceptor.readFromUrl(httpRequest.pathInfo, null,"DiagnosticReport")
        return if (resource is DiagnosticReport) resource else null
    }

    @Delete
    fun delete(theRequest: HttpServletRequest, @IdParam theId: IdType): MethodOutcome? {
        return awsDiagnosticReport.delete(theId)
    }

    @Operation(name = "document", idempotent = true, manualResponse = true)
    fun convertOpenAPI(
        servletRequest: HttpServletRequest,
        servletResponse: HttpServletResponse,
        @IdParam reportId: IdType,
        @OperationParam(name= "_format") format: StringType?) {
        var patientSummary = FHIRDocument(client,ctxFHIR,templateEngine)

        var bundle = patientSummary.getDiagnosticReport(reportId.idPart)
        if (format !== null && (format.value.contains("pdf") || format.value.contains("text") )) {
            val xmlResult = ctxFHIR.newXmlParser().setPrettyPrint(true).encodeResourceToString(bundle)
            val html = patientSummary.convertHTML(xmlResult, "XML/DocumentToHTML.xslt")
            if (html !== null) {
                if (format.value.contains("text")) {
                    servletResponse.setContentType("text/html")
                    servletResponse.setCharacterEncoding("UTF-8")
                    servletResponse.writer.write(html)
                    servletResponse.writer.flush()
                    return
                } else if (format.value.contains("pdf")) {
                    servletResponse.setContentType("application/pdf")
                    servletResponse.setCharacterEncoding("UTF-8")
                    var diagnosticReport: DiagnosticReport? = null
                    bundle.entry.forEach{
                        if (it.hasResource() && it.resource is DiagnosticReport) diagnosticReport = it.resource as DiagnosticReport
                    }
                    if (diagnosticReport !== null && diagnosticReport!!.hasPresentedForm()
                        && diagnosticReport!!.presentedFormFirstRep.hasContentType()
                        && diagnosticReport!!.presentedFormFirstRep.contentType.equals("application/pdf")) {

                        val os: OutputStream = servletResponse.getOutputStream()
                        val byteArray = diagnosticReport!!.presentedFormFirstRep.data
                        try {
                            os.write(byteArray, 0, byteArray.size)
                        } catch (excp: Exception) {
                            //handle error
                        } finally {
                            os.close()
                        }
                        // has been flushed    servletResponse.writer.flush()
                        return
                    } else {
                        var pdfOutputStream = patientSummary.convertPDF(html)
                        if (pdfOutputStream !== null) {
                            val os: OutputStream = servletResponse.getOutputStream()
                            val byteArray = pdfOutputStream.toByteArray()

                            try {
                                os.write(byteArray, 0, byteArray.size)
                            } catch (excp: Exception) {
                                //handle error
                            } finally {
                                os.close()
                            }
                            // has been flushed    servletResponse.writer.flush()
                            return
                        }
                    }
                }
            }
        }
        servletResponse.setContentType("application/json")
        servletResponse.setCharacterEncoding("UTF-8")
        servletResponse.writer.write(ctxFHIR.newJsonParser().setPrettyPrint(true).encodeResourceToString(bundle))
        servletResponse.writer.flush()
        return
    }


}
