/*
 * SolarServer.java
 *
 * Implements the SmartSolar gRPC service for a smart home energy system.
 * Registers the service with jmDNS for discovery on the local network.
 */

package server;

import java.io.IOException;
import solar.protos.SmartSolarServiceGrpc.SmartSolarServiceImplBase;
import solar.protos.GetDailyYieldRequest;
import solar.protos.GetDailyYieldResponse;
import solar.protos.RealTimeOutput;
import solar.protos.TradeRequest;
import solar.protos.TradeResponse;
import java.net.InetAddress;
import java.time.Instant;
import java.util.Random;
import java.util.logging.Logger;
import io.grpc.Server;
import io.grpc.ServerBuilder;
import io.grpc.stub.StreamObserver;
import javax.jmdns.JmDNS;
import javax.jmdns.ServiceInfo;
import com.google.protobuf.Empty;

public class SolarServer extends SmartSolarServiceImplBase {
	private static final Logger logger = Logger.getLogger(ThermostatServer.class.getName());
	private final Random random = new Random(); //Random generator for simulated data
	
	public static void main(String[] args) throws IOException, InterruptedException {
		SolarServer solarservice = new SolarServer();
		int port = 50052; //Port where the gRPC server will listen
		
		//Build and start the gRPC server
		Server server = ServerBuilder
				.forPort(port)
				.addService(solarservice)
				.build()
				.start();
		
		logger.info("Solar server started, listening on " + port);
		
		//Register service via jmDNS for discovery on local network
		JmDNS jmdns = JmDNS.create(InetAddress.getLocalHost());
		//(Service type, Service name, Service port, Service description)
		ServiceInfo serviceInfo = ServiceInfo.create("_solarpanel._grpc._tcp.local.", "SolarPanelService", port, "Solar Panel Management Service");
		jmdns.registerService(serviceInfo);
		System.out.println("Starting the Solar Server loop");
		
		//Wait until server is terminated
		server.awaitTermination();
	}
	
	@Override
	public void getDailyYield(GetDailyYieldRequest request, StreamObserver<GetDailyYieldResponse> responseObserver) {
		
		//simulate daily yield and peak
		double peak = 5.0 + random.nextGaussian() * 0.5; //Peak power around 5 kW
		double total = peak * 6.0 + random.nextGaussian() * 2.0; //Total yield ~6h of peak
		
		//Build and send the response
		GetDailyYieldResponse resp = GetDailyYieldResponse.newBuilder()
				.setYieldKw(total)
				.setPeak(peak)
				.build();
		
		responseObserver.onNext(resp); //Send response
		responseObserver.onCompleted(); //Complete the stream
	}
	
	@Override
	public void streamRealTimeOutput(Empty request, StreamObserver<RealTimeOutput> responseObserver) {
		try {
			while(!Thread.currentThread().isInterrupted()) {
				
				//Simulate current output with sine-wave + noise
				double current = 4.0 + Math.sin(System.currentTimeMillis() / 1_000.0) + random.nextGaussian() * 0.2;
				String timestamp = Instant.now().toString(); //Current timestamp
				
				//Build and send the response
				RealTimeOutput out = RealTimeOutput.newBuilder() 
						.setCurrentKw(current)
						.setTimestamp(timestamp)
						.build();
				responseObserver.onNext(out);
				
				Thread.sleep(1_000); //Wait 1 second before next reading
			}
		}catch(InterruptedException ie) {
			Thread.currentThread().interrupt(); //Error Handling
		}finally {
			responseObserver.onCompleted(); //Complete the stream
		}
	}
	
	@Override
	public StreamObserver<TradeRequest> energyTradeNegotiation(StreamObserver<TradeResponse> responseObserver){
		//Returns a StreamObserver to handle incoming trade requests
		return new StreamObserver<TradeRequest>() {
			private final double counterOfferPrice = 0.40; //Fixed counter-offer
			
			@Override
			public void onNext(TradeRequest req) {
				TradeResponse resp;
				if(req.getPrice() <= counterOfferPrice) {
					
					//Accept the trade if price is at or below counter-offer
					resp = TradeResponse.newBuilder()
							.setAccepted(true)
							.setAgreedPrice(req.getPrice()).build();
				}else {
					//Otherwise send a counter-offer
					resp = TradeResponse.newBuilder()
							.setAccepted(false)
							.setCounterOffer(counterOfferPrice)
							.build();
				}
				responseObserver.onNext(resp); //Send response
			}
			
			@Override
			public void onError(Throwable t) {
				logger.warning("Trade negotiation error: " + t.getMessage()); //Error Handling
			}
			
			@Override
			public void onCompleted() {
				responseObserver.onCompleted(); //Complete the stream
			}
		};
	}
}