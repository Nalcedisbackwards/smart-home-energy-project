package server;

import java.io.IOException;
import thermostat.protos.SmartThermostatGrpc.SmartThermostatImplBase;
import thermostat.protos.SetTargetTemperatureRequest;
import thermostat.protos.SetTargetTemperatureResponse;
import thermostat.protos.GetTemperatureHistoryRequest;
import thermostat.protos.TemperatureReading;
import thermostat.protos.GetAverageTemperatureResponse;
import java.net.InetAddress;
import java.util.logging.Logger;
import io.grpc.Server;
import io.grpc.ServerBuilder;
import io.grpc.stub.StreamObserver;
import javax.jmdns.JmDNS;
import javax.jmdns.ServiceInfo;


public class ThermostatServer extends SmartThermostatImplBase {
	
	private volatile double currentTargetTemp = 20.0;
	
	private static final Logger logger = Logger.getLogger(ThermostatServer.class.getName());
	
	public static void main(String[] args) throws IOException, InterruptedException {
		
		ThermostatServer thermostatserver = new ThermostatServer();
		
		int port = 50051;
		Server server = ServerBuilder
				.forPort(port)
				.addService(thermostatserver)
				.build()
				.start();
		
		logger.info("Thermostat server started, listening on " + port);
		
		
		
		JmDNS jmdns = JmDNS.create(InetAddress.getLocalHost());
		
		ServiceInfo serviceInfo = ServiceInfo.create("_thermostat._grpc._tcp.local.", "ThermostatService", port, "Thermostat Server will give you the current temperature");
		jmdns.registerService(serviceInfo);
		System.out.println("Starting the Thermostat Server loop");
		
		server.awaitTermination();
		
	}
	
	@Override
	 public void setTargetTemperature(SetTargetTemperatureRequest req, StreamObserver<SetTargetTemperatureResponse> responseObserver) {
		double newTemp = req.getTargetTemp(); // Read the requested temperature
		currentTargetTemp = newTemp;
		
		SetTargetTemperatureResponse reply = SetTargetTemperatureResponse.newBuilder()
				.setSuccess(true)
				.build();
		responseObserver.onNext(reply);
		responseObserver.onCompleted();
	}
	
	@Override
    public void streamTemperatureHistory(GetTemperatureHistoryRequest req, StreamObserver<TemperatureReading> responseObserver) {
		responseObserver.onCompleted();
	}
	
	@Override
	 public StreamObserver<TemperatureReading> getAverageTemperature(StreamObserver<GetAverageTemperatureResponse> responseObserver) {
		return new StreamObserver<TemperatureReading>() {
			 @Override
		     public void onNext(TemperatureReading reading) {
		            
		     }
			 
			 @Override
			 public void onError(Throwable t) {
		      
			 }
			 
			 @Override
		     public void onCompleted() {
				 responseObserver.onCompleted();
			 }
		};
	}
	
}