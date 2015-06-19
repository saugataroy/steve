package de.rwth.idsg.steve.service;

import de.rwth.idsg.steve.ocpp.OcppConstants;
import de.rwth.idsg.steve.ocpp.OcppProtocol;
import de.rwth.idsg.steve.repository.OcppServerRepository;
import de.rwth.idsg.steve.utils.DateTimeUtils;
import lombok.extern.slf4j.Slf4j;
import ocpp.cs._2012._06.*;
import org.joda.time.DateTime;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;

/**
 * Transport-level agnostic OCPP 1.5 server service which contains the actual business logic.
 *
 * @author Sevket Goekay <goekay@dbis.rwth-aachen.de>
 * @since 13.03.2015
 */
@Slf4j
@Service
public class CentralSystemService15_Service {

    @Autowired private OcppServerRepository ocppServerRepository;
    @Autowired private UserService userService;
    @Autowired private OcppConstants ocppConstants;

    public BootNotificationResponse bootNotification(BootNotificationRequest parameters, String chargeBoxIdentity,
                                                     OcppProtocol ocppProtocol) {
        log.debug("Executing bootNotification for {}", chargeBoxIdentity);

        DateTime now = new DateTime();

        boolean isRegistered = ocppServerRepository.updateChargebox(ocppProtocol,
                                                                    parameters.getChargePointVendor(),
                                                                    parameters.getChargePointModel(),
                                                                    parameters.getChargePointSerialNumber(),
                                                                    parameters.getChargeBoxSerialNumber(),
                                                                    parameters.getFirmwareVersion(),
                                                                    parameters.getIccid(),
                                                                    parameters.getImsi(),
                                                                    parameters.getMeterType(),
                                                                    parameters.getMeterSerialNumber(),
                                                                    chargeBoxIdentity,
                                                                    new Timestamp(now.getMillis()));

        RegistrationStatus status = isRegistered ? RegistrationStatus.ACCEPTED : RegistrationStatus.REJECTED;

        return new BootNotificationResponse()
                .withStatus(status)
                .withCurrentTime(now)
                .withHeartbeatInterval(ocppConstants.getHeartbeatIntervalInSeconds());
    }

    public FirmwareStatusNotificationResponse firmwareStatusNotification(FirmwareStatusNotificationRequest parameters,
                                                                         String chargeBoxIdentity) {
        log.debug("Executing firmwareStatusNotification for {}", chargeBoxIdentity);

        String status = parameters.getStatus().value();
        ocppServerRepository.updateChargeboxFirmwareStatus(chargeBoxIdentity, status);
        return new FirmwareStatusNotificationResponse();
    }

    public StatusNotificationResponse statusNotification(StatusNotificationRequest parameters, String chargeBoxIdentity) {
        log.debug("Executing statusNotification for {}", chargeBoxIdentity);

        // Mandatory fields
        int connectorId = parameters.getConnectorId();
        String status = parameters.getStatus().value();
        String errorCode = parameters.getErrorCode().value();

        // Optional fields
        String errorInfo = parameters.getInfo();
        Timestamp timestamp;
        if (parameters.isSetTimestamp()) {
            timestamp = new Timestamp(parameters.getTimestamp().getMillis());
        } else {
            timestamp = DateTimeUtils.getCurrentTimestamp();
        }
        String vendorId = parameters.getVendorId();
        String vendorErrorCode = parameters.getVendorErrorCode();

        ocppServerRepository.insertConnectorStatus15(chargeBoxIdentity, connectorId, status, timestamp,
                errorCode, errorInfo, vendorId, vendorErrorCode);

        return new StatusNotificationResponse();
    }

    public MeterValuesResponse meterValues(MeterValuesRequest parameters, String chargeBoxIdentity) {
        log.debug("Executing meterValues for {}", chargeBoxIdentity);

        int connectorId = parameters.getConnectorId();
        Integer transactionId = parameters.getTransactionId();
        if (parameters.isSetValues()) {
            ocppServerRepository.insertMeterValues15(chargeBoxIdentity, connectorId, parameters.getValues(), transactionId);
        }
        return new MeterValuesResponse();
    }

    public DiagnosticsStatusNotificationResponse diagnosticsStatusNotification(DiagnosticsStatusNotificationRequest parameters,
                                                                               String chargeBoxIdentity) {
        log.debug("Executing diagnosticsStatusNotification for {}", chargeBoxIdentity);

        String status = parameters.getStatus().value();
        ocppServerRepository.updateChargeboxDiagnosticsStatus(chargeBoxIdentity, status);
        return new DiagnosticsStatusNotificationResponse();
    }

    public StartTransactionResponse startTransaction(StartTransactionRequest parameters, String chargeBoxIdentity) {
        log.debug("Executing startTransaction for {}", chargeBoxIdentity);

        // Get the authorization info of the user
        String idTag = parameters.getIdTag();
        IdTagInfo idTagInfo = userService.getIdTagInfoV15(idTag);

        StartTransactionResponse response = new StartTransactionResponse().withIdTagInfo(idTagInfo);

        if (AuthorizationStatus.ACCEPTED.equals(idTagInfo.getStatus())) {
            int connectorId = parameters.getConnectorId();
            Integer reservationId = parameters.getReservationId();
            Timestamp startTimestamp = new Timestamp(parameters.getTimestamp().getMillis());

            String startMeterValue = Integer.toString(parameters.getMeterStart());
            Integer transactionId = ocppServerRepository.insertTransaction15(chargeBoxIdentity, connectorId, idTag,
                    startTimestamp, startMeterValue,
                    reservationId);

            response.setTransactionId(transactionId);
        }
        return response;
    }

    public StopTransactionResponse stopTransaction(StopTransactionRequest parameters, String chargeBoxIdentity) {
        log.debug("Executing stopTransaction for {}", chargeBoxIdentity);

        // Get parameters and update transaction in DB
        int transactionId = parameters.getTransactionId();
        Timestamp stopTimestamp = new Timestamp(parameters.getTimestamp().getMillis());
        String stopMeterValue = Integer.toString(parameters.getMeterStop());
        ocppServerRepository.updateTransaction(transactionId, stopTimestamp, stopMeterValue);

        /**
         * If TransactionData is included:
         *
         * Aggregate MeterValues from multiple TransactionData in one big, happy list. TransactionData is just
         * a container for MeterValues without any additional data/semantics anyway. This enables us to write
         * into DB in one repository call (query), rather than multiple calls as it was before (for each TransactionData)
         *
         * Saved the world again with this micro-optimization.
         */
        if (parameters.isSetTransactionData()) {
            List<MeterValue> combinedList = new ArrayList<>();
            for (TransactionData data : parameters.getTransactionData()) {
                combinedList.addAll(data.getValues());
            }
            ocppServerRepository.insertMeterValuesOfTransaction(chargeBoxIdentity, transactionId, combinedList);
        }

        // Get the authorization info of the user
        if (parameters.isSetIdTag()) {
            IdTagInfo idTagInfo = userService.getIdTagInfoV15(parameters.getIdTag());
            return new StopTransactionResponse().withIdTagInfo(idTagInfo);
        } else {
            return new StopTransactionResponse();
        }
    }

    public HeartbeatResponse heartbeat(HeartbeatRequest parameters, String chargeBoxIdentity) {
        log.debug("Executing heartbeat for {}", chargeBoxIdentity);

        DateTime now = new DateTime();
        ocppServerRepository.updateChargeboxHeartbeat(chargeBoxIdentity, new Timestamp(now.getMillis()));

        return new HeartbeatResponse().withCurrentTime(now);
    }

    public AuthorizeResponse authorize(AuthorizeRequest parameters, String chargeBoxIdentity) {
        log.debug("Executing authorize for {}", chargeBoxIdentity);

        // Get the authorization info of the user
        String idTag = parameters.getIdTag();
        IdTagInfo idTagInfo = userService.getIdTagInfoV15(idTag);

        return new AuthorizeResponse().withIdTagInfo(idTagInfo);
    }

    // Dummy implementation. This is new in OCPP 1.5. It must be vendor-specific.
    public DataTransferResponse dataTransfer(DataTransferRequest parameters, String chargeBoxIdentity) {
        log.debug("Executing dataTransfer for {}", chargeBoxIdentity);

        log.info("[Data Transfer] Charge point: {}, Vendor Id: {}", chargeBoxIdentity, parameters.getVendorId());
        if (parameters.isSetMessageId()) log.info("[Data Transfer] Message Id: {}", parameters.getMessageId());
        if (parameters.isSetData()) log.info("[Data Transfer] Data: {}", parameters.getData());

        return new DataTransferResponse();
    }
}
