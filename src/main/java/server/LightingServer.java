/*
 * LightingServer.java
 *
 * Implements the SmartLighting gRPC service for a smart home system.
 * Registers the service using jmDNS for local network discovery.
 */

package server;

import java.io.IOException;
import java.net.InetAddress;
import java.time.Instant;
import java.util.Random;
import java.util.concurrent.atomic.DoubleAdder;
import java.util.logging.Logger;
import javax.jmdns.JmDNS;
import javax.jmdns.ServiceInfo;
import io.grpc.Server;
import io.grpc.ServerBuilder;
import io.grpc.stub.StreamObserver;
import lighting.protos.SmartLightingServiceGrpc.SmartLightingServiceImplBase;
import lighting.protos.GetCurrentBrightnessRequest;
import lighting.protos.GetCurrentBrightnessResponse;
import lighting.protos.StreamAmbientLightDataRequest;
import lighting.protos.AmbientLightReading;
import lighting.protos.LightUsageStat;
import lighting.protos.UploadLightUsageResponse;
import lighting.protos.AdjustBrightnessRequest;
import lighting.protos.AdjustBrightnessResponse;

public class LightingServer extends SmartLightingServiceImplBase {
	private static final Logger logger = Logger.getLogger(LightingServer.class.getName());
	private final Random random = new Random(); //Random generator for simulated data
	
	public static void main(String[] args) throws IOException, InterruptedException {
		LightingServer lightingservice = new LightingServer();
		int port = 50053; //Port where the gRPC server will listen
		
		//Build and start the gRPC server
		Server server = ServerBuilder
				.forPort(port)
				.addService(lightingservice)
				.build()
				.start();
		
		logger.info("Lighting server started, listening on " + port);
		
		//Register service via jmDNS for discovery on local network
        JmDNS jmdns = JmDNS.create(InetAddress.getLocalHost());
        //(Service type, Service name, Service port, Service description)
        ServiceInfo info = ServiceInfo.create("_smartlighting._grpc._tcp.local.", "SmartLightingService", port, "Smart Lighting Management Service");
        jmdns.registerService(info);
        System.out.println("Starting the Lighting Server loop");
        
        //Wait until server is terminated
        server.awaitTermination();
    }
	
	@Override
    public void getCurrentBrightness(GetCurrentBrightnessRequest req, StreamObserver<GetCurrentBrightnessResponse> respObs) {
        
        int level = random.nextInt(101); //Simulate brightness 0–100%
        String ts = Instant.now().toString(); //Current Timestamp
        
        //Build response with level and timestamp
        GetCurrentBrightnessResponse resp = GetCurrentBrightnessResponse.newBuilder()
                .setLevel(level)
                .setTimestamp(ts)
                .build();
        
        respObs.onNext(resp); //Send response
        respObs.onCompleted(); //Complete the stream
    }
	
	@Override
    public void streamAmbientLightData(StreamAmbientLightDataRequest req, StreamObserver<AmbientLightReading> respObs) {
        
		//Simulate a reading every 5 seconds
        try {
            while (!Thread.currentThread().isInterrupted()) {
                double lux = 50 + random.nextDouble() * 550; //Simulated lux between 50 and 600
                boolean occupied = random.nextBoolean(); //Random occupancy status
                String ts = Instant.now().toString(); //Current timestamp
                
                //Build and send a reading
                AmbientLightReading reading = AmbientLightReading.newBuilder()
                        .setLux(lux)
                        .setOccupied(occupied)
                        .setTimestamp(ts)
                        .build();
                respObs.onNext(reading);

                Thread.sleep(5_000); //Pause for 5 seconds before next reading
            }
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt(); //Error handling
        } finally {
            respObs.onCompleted(); //Complete the stream
        }
    }
	
	@Override
    public StreamObserver<LightUsageStat> uploadLightUsageStats(StreamObserver<UploadLightUsageResponse> respObs) {
		
		//Total energy consumed from client-streamed stats
		final DoubleAdder totalEnergy = new DoubleAdder();
        final double MAX_POWER_KW = 0.1;    //0.1 kW at 100% brightness

        return new StreamObserver<LightUsageStat>() {
            @Override
            public void onNext(LightUsageStat stat) {
               
            	//Scales level to 0-100
            	double level = random.nextDouble() * 100.0;
                for (int minute = 0; minute < stat.getDurationMin(); minute++) {
                	double rnd = 1 + (random.nextGaussian() * 0.1);
                	//10% variability around the baseline fraction
                	double noisyLevel = level * rnd;
                	//Energy per minute = P_max * fraction * (1/60) hour
                	double energyThisMinute = MAX_POWER_KW * noisyLevel / 60.0;
                	totalEnergy.add(energyThisMinute);
                }
            }

            @Override
            public void onError(Throwable t) {
                logger.warning("uploadLightUsageStats error: " + t.getMessage()); //Error Handling
            }

            @Override
            public void onCompleted() {
            	double energyKw = totalEnergy.sum();
            	//Build and send response with total energy
                UploadLightUsageResponse resp = UploadLightUsageResponse.newBuilder()
                        .setTotalEnergyKw(energyKw)
                        .build();
                
                respObs.onNext(resp); //Send response
                respObs.onCompleted(); //Complete the stream
            }
        };
    }
	
	@Override
    public StreamObserver<AdjustBrightnessRequest> adjustBrightness(StreamObserver<AdjustBrightnessResponse> respObs) {
		//Handle brightness adjustment stream
        return new StreamObserver<AdjustBrightnessRequest>() {
            @Override
            public void onNext(AdjustBrightnessRequest req) {
            	//Compute simulated lux based on desired level and occupancy
                double factor = req.getOccupied() ? 10.0 : 5.0;
                double lux = req.getDesiredLevel() * factor / 100.0 * 600.0
                             + random.nextGaussian() * 10.0;
                String ts = req.getTimestamp();
                
             //Build and send adjustment response
                AdjustBrightnessResponse resp = AdjustBrightnessResponse.newBuilder()
                        .setLux(lux)
                        .setTimestamp(ts)
                        .build();
                respObs.onNext(resp);
            }

            @Override
            public void onError(Throwable t) {
                logger.warning("adjustBrightness error: " + t.getMessage()); //Error handling
            }

            @Override
            public void onCompleted() {
                respObs.onCompleted(); //Complete the stream
            }
        };
    }
}