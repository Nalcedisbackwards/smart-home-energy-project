package client;

import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import thermostat.protos.SetTargetTemperatureRequest;
import thermostat.protos.SmartThermostatGrpc;
import thermostat.protos.SetTargetTemperatureRequest;
import thermostat.protos.SetTargetTemperatureResponse;
import io.grpc.StatusRuntimeException;

public class ThermostatClient {
	
	private static Logger logger = Logger.getLogger(ThermostatClient.class.getName());
	
	
	public static void main(String[] args) throws InterruptedException {
        String host = "localhost";
        int port = 50051;
    
        ManagedChannel channel = ManagedChannelBuilder.
				forAddress(host, port)
				.usePlaintext()
				.build();
        
        SmartThermostatGrpc.SmartThermostatBlockingStub blockingStub = SmartThermostatGrpc.newBlockingStub(channel);

        
        
        try {
        	double targetTemp = 22.5;
        	SetTargetTemperatureRequest request = SetTargetTemperatureRequest.newBuilder().setTargetTemp(targetTemp).build();
        	
        	SetTargetTemperatureResponse response = blockingStub.setTargetTemperature(request);
        	
        	logger.info("SetTargetTemperature(" + targetTemp + ") → success=" + response.getSuccess());
        } catch (StatusRuntimeException e) {
        	logger.log(Level.WARNING, "RPC failed: {0}", e.getStatus());
        } finally {
        	channel.shutdown().awaitTermination(5, TimeUnit.SECONDS);
        }
	}
	
}