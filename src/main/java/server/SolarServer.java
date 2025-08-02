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

/*
 * SolarServer implements the SmartSolar gRPC services defined in the proto file. 
 */

public class SolarServer extends SmartSolarServiceImplBase {
	private static final Logger logger = Logger.getLogger(ThermostatServer.class.getName());
	private final Random random = new Random();
	
	public static void main(String[] args) throws IOException, InterruptedException {
		SolarServer solarservice = new SolarServer();
		int port = 50052;
		Server server = ServerBuilder
				.forPort(port)
				.addService(solarservice)
				.build()
				.start();
		
		logger.info("Thermostat server started, listening on " + port);
		
		//jmDNS registration on advertise the service on the local network
		JmDNS jmdns = JmDNS.create(InetAddress.getLocalHost());
		ServiceInfo serviceInfo = ServiceInfo.create("_solarpanel._grpc._tcp.local.", "SolarPanelService", port, "Solar Panel Management Service");
		jmdns.registerService(serviceInfo);
		System.out.println("Starting the Thermostat Server loop");
		
		server.awaitTermination();//keep server running
	}
	
	@Override
	public void getDailyYield(GetDailyYieldRequest request, StreamObserver<GetDailyYieldResponse> responseObserver) {
		//simulate daily yield and peak
		double peak = 5.0 + random.nextGaussian() * 0.5;
		double total = peak * 6.0 + random.nextGaussian() * 2.0;
		GetDailyYieldResponse resp = GetDailyYieldResponse.newBuilder()
				.setYieldKw(total)
				.setPeak(peak)
				.build();
		responseObserver.onNext(resp);
		responseObserver.onCompleted();
	}
	
	@Override
	public void streamRealTimeOutput(Empty request, StreamObserver<RealTimeOutput> responseObserver) {
		try {
			while(!Thread.currentThread().isInterrupted()) {
				// Simulate current output with sine-wave + noise
				double current = 4.0 + Math.sin(System.currentTimeMillis() / 1_000.0) + random.nextGaussian() * 0.2;
				String timestamp = Instant.now().toString();
				RealTimeOutput out = RealTimeOutput.newBuilder()
						.setCurrentKw(current)
						.setTimestamp(timestamp)
						.build();
				responseObserver.onNext(out);
				Thread.sleep(1_000);
			}
		}catch(InterruptedException ie) {
			Thread.currentThread().interrupt();
		}finally {
			responseObserver.onCompleted();
		}
	}
	
	@Override
	public StreamObserver<TradeRequest> energyTradeNegotiation(StreamObserver<TradeResponse> responseObserver){
		return new StreamObserver<TradeRequest>() {
			private final double counterOfferPrice = 0.40;
			
			@Override
			public void onNext(TradeRequest req) {
				TradeResponse resp;
				if(req.getPrice() <= counterOfferPrice) {
					//accept offer
					resp = TradeResponse.newBuilder()
							.setAccepted(true)
							.setAgreedPrice(req.getPrice()).build();
				}else {
					//counter with lower price
					resp = TradeResponse.newBuilder()
							.setAccepted(false)
							.setCounterOffer(counterOfferPrice)
							.build();
				}
				responseObserver.onNext(resp);;
			}
			
			@Override
			public void onError(Throwable t) {
				logger.warning("Trade negotiation error: " + t.getMessage());
			}
			
			@Override
			public void onCompleted() {
				responseObserver.onCompleted();
			}
		};
	}
}