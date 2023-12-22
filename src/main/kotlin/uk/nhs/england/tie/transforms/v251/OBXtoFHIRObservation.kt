package uk.nhs.england.tie.transforms.v251

import ca.uhn.hl7v2.model.v251.datatype.NM
import ca.uhn.hl7v2.model.v251.segment.OBX
import ca.uhn.hl7v2.model.v251.segment.ORC
import mu.KLogging
import org.apache.commons.collections4.Transformer
import org.hl7.fhir.r4.model.Coding
import org.hl7.fhir.r4.model.DiagnosticReport
import org.hl7.fhir.r4.model.Observation
import org.hl7.fhir.r4.model.Quantity


class OBXtoFHIRObservation : Transformer<OBX, Observation> {
    companion object : KLogging()
    override fun transform(obx : OBX?): Observation {
        var observation = Observation()
        if (obx !== null) {
            if (obx.observationIdentifier != null && obx.observationIdentifier.identifier !== null) {
                observation.code.addCoding(
                    Coding()
                        .setCode(obx.observationIdentifier.identifier.value)
                        .setDisplay(obx.observationIdentifier.text.value)
                )
            }
            if (obx.dateTimeOfTheObservation !== null ) {
                observation.effectiveDateTimeType.value = obx.dateTimeOfTheObservation.time.valueAsDate
            }
            var quantity = Quantity()
            if (obx.observationValue !== null) {
                obx.observationValue.forEach {
                    if (it.data is NM) {
                        logger.info((it.data as NM).value)
                        quantity.value = (it.data as NM).value.toBigDecimal()
                    }
                }

            }
            if (obx.units !== null) {
                quantity.unit = (obx.units.identifier).value
            }
            if (quantity.value !== null) observation.setValue(quantity)

        }
        return observation
    }

}