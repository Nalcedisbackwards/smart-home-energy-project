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

/*
 * ThermostatServer implements the SmartThermostat gRPC services defined in the proto file. 
 */

public class ThermostatServer extends SmartThermostatImplBase {
	
	//Initial target temp to be set upon starting
	private volatile double currentTargetTemp = 20.0;
	
	
	//
	private static final Logger logger = Logger.getLogger(ThermostatServer.class.getName());
	
	//Random genrator to simulate noise in temperature readings
	private final Random random = new Random();
	
	
	//Main method to start the gRPC server and register service via jmDNS discovery
	public static void main(String[] args) throws IOException, InterruptedException {
		ThermostatServer thermostatservice = new ThermostatServer();
		int port = 50051;//Network port for gRPC service
		
		//Build and start the gRPC server
		Server server = ServerBuilder
				.forPort(port)
				.addService(thermostatservice)
				.build()
				.start();
		
		logger.info("Thermostat server started, listening on " + port);
		
		
		//jmDNS registration on advertise the service on the local network
		JmDNS jmdns = JmDNS.create(InetAddress.getLocalHost());
		
		ServiceInfo serviceInfo = ServiceInfo.create("_thermostat._grpc._tcp.local.", "ThermostatService", port, "Thermostat Server will give you the current temperature");
		jmdns.registerService(serviceInfo);
		System.out.println("Starting the Thermostat Server loop");
		
		server.awaitTermination();//keep server running
		
	}
	
	
	//Unary RPC: Sets new target temp
	@Override
	public void setTargetTemperature(SetTargetTemperatureRequest req, StreamObserver<SetTargetTemperatureResponse> responseObserver) {

	double newTemp = req.getTargetTemp(); // Read the requested temperature
		currentTargetTemp = newTemp;
		
		//build and send a success response
		SetTargetTemperatureResponse reply = SetTargetTemperatureResponse.newBuilder()
				.setSuccess(true)
				.build();
		responseObserver.onNext(reply);
		responseObserver.onCompleted();
	}
	
	//Server streaming RPC: Streams simulated temperature readings between two timestamps
	@Override
    public void streamTemperatureHistory(GetTemperatureHistoryRequest req, StreamObserver<TemperatureReading> responseObserver) {
		long startTH = req.getStartTimestamp(); //start time in ms
		long endTH = req.getEndTimestamp(); //end time in ms
		long stepTH = 1_000; //interval between samples = 1 sec
		
		//loop from start to end timestamp sending 1 reading per sec
		for (long i = startTH; i <= endTH; i += stepTH) {
			//simulate temp with noise around current target
			double noisyTemp = currentTargetTemp + random.nextGaussian()*0.5;
			
			//build TemperatureReading message
			TemperatureReading reading = TemperatureReading.newBuilder()
					.setTemperature(noisyTemp)
					.setTimestamp(i)
					.build();
			//send each reading to the client
			responseObserver.onNext(reading);
		}
		responseObserver.onCompleted();
	}
	
	//Client streaming RPC: recives multiple TemperatureReading messages and computes the average
	@Override
	 public StreamObserver<TemperatureReading> getAverageTemperature(StreamObserver<GetAverageTemperatureResponse> responseObserver) {
		return new StreamObserver<TemperatureReading>() {
			private double sum = 0; //sum of the recived temps
			private int count = 0; //number of readings recived
			 @Override
		     public void onNext(TemperatureReading reading) {
				 //sums the readings and increments count
				 sum += reading.getTemperature();
				 count++;
		     }
			 
			 @Override
			 public void onError(Throwable t) {
				 logger.warning("Stream error: " + t);
			 }
			 
			 @Override
		     public void onCompleted() {
				 //calc average
				 double average = sum/count;
				 
				 //build response
				 GetAverageTemperatureResponse resp = GetAverageTemperatureResponse.newBuilder()
						 .setAverageTemp(average)
						 .build();
				 
				 responseObserver.onNext(resp);
				 responseObserver.onCompleted();
			 }
		};
	}
	
}