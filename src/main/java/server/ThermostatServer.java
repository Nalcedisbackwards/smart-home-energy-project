/*
 * SolarServer.java
 *
 * Implements the SmartSolar gRPC service for a smart home energy system.
 * Registers the service with jmDNS for discovery on the local network.
 */

package server;

import java.io.IOException;
import thermostat.protos.SmartThermostatGrpc.SmartThermostatImplBase;
import thermostat.protos.SetTargetTemperatureRequest;
import thermostat.protos.SetTargetTemperatureResponse;
import thermostat.protos.GetTemperatureHistoryRequest;
import thermostat.protos.TemperatureReading;
import thermostat.protos.GetAverageTemperatureResponse;
import java.net.InetAddress;
import java.util.Random;
import java.util.logging.Logger;
import io.grpc.Server;
import io.grpc.ServerBuilder;
import io.grpc.stub.StreamObserver;
import javax.jmdns.JmDNS;
import javax.jmdns.ServiceInfo;

public class ThermostatServer extends SmartThermostatImplBase {

	private static final Logger logger = Logger.getLogger(ThermostatServer.class.getName());
	private final Random random = new Random(); //Random generator for simulating data
	
	public static void main(String[] args) throws IOException, InterruptedException {
		ThermostatServer thermostatservice = new ThermostatServer();
		int port = 50051; //Port where the gRPC server will listen
		
		//Build and start the gRPC server
		Server server = ServerBuilder
				.forPort(port)
				.addService(thermostatservice)
				.build()
				.start();
		
		logger.info("Thermostat server started, listening on " + port);
		
		//Register service via jmDNS for discovery on local network
		JmDNS jmdns = JmDNS.create(InetAddress.getLocalHost());
		//(Service type, Service name, Service port, Service description)
		ServiceInfo serviceInfo = ServiceInfo.create("_thermostat._grpc._tcp.local.", "ThermostatService", port, "Thermostat Server will give you the current temperature");
		jmdns.registerService(serviceInfo);
		System.out.println("Starting the Thermostat Server loop");
		
		//Wait until server is terminated
		server.awaitTermination();
		
	}
	
	private volatile double currentTargetTemp = 20.0; //Init target temp
	
	@Override
	public void setTargetTemperature(SetTargetTemperatureRequest req, StreamObserver<SetTargetTemperatureResponse> responseObserver) {

		currentTargetTemp = req.getTargetTemp(); //Read the target temperature
		
		//Build and send the response
		SetTargetTemperatureResponse reply = SetTargetTemperatureResponse.newBuilder()
				.setSuccess(true)
				.build();
		
		responseObserver.onNext(reply); //Send response
		responseObserver.onCompleted(); //Complete the stream
	}
	
	@Override
    public void streamTemperatureHistory(GetTemperatureHistoryRequest req, StreamObserver<TemperatureReading> responseObserver) {
		long startTH = req.getStartTimestamp(); //Start time in ms
		long endTH = req.getEndTimestamp(); //End time in ms
		long stepTH = 1_000; //Interval between samples = 1 sec
		
		//Loop through timestamps and send readings
		for (long i = startTH; i <= endTH; i += stepTH) {
			double noisyTemp = currentTargetTemp + random.nextGaussian()*0.5; //Add Gaussian noise
			
			//Build and send response
			TemperatureReading reading = TemperatureReading.newBuilder()
					.setTemperature(noisyTemp)
					.setTimestamp(i)
					.build();
			responseObserver.onNext(reading);
		}
		responseObserver.onCompleted(); //Complete the stream
	}
	
	@Override
	 public StreamObserver<TemperatureReading> getAverageTemperature(StreamObserver<GetAverageTemperatureResponse> responseObserver) {
		
		//Receive multiple readings and compute their average
		return new StreamObserver<TemperatureReading>() {
			private double sum = 0; //sum of the received temps
			private int count = 0; //number of readings received
			
			 @Override
		     public void onNext(TemperatureReading reading) {
				 
				 //Sums the readings and increments count
				 sum += reading.getTemperature();
				 count++;
		     }
			 
			 @Override
			 public void onError(Throwable t) {
				 logger.warning("Stream error: " + t); //Error handling
			 }
			 
			 @Override
		     public void onCompleted() {
				 double average = sum/count; //Calc average
				 
				 //Build response
				 GetAverageTemperatureResponse resp = GetAverageTemperatureResponse.newBuilder()
						 .setAverageTemp(average)
						 .build();
				 
				 responseObserver.onNext(resp); //Send response
				 responseObserver.onCompleted(); //Complete the stream
			 }
		};
	}
	
}