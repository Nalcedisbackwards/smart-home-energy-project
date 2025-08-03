/*
 * SolarClient.java
 *
 * Client implementation for the SmartSolar gRPC service. 
 * Establishes a channel to the SolarServer. 
 * Offers methods for each RPC.
 * Includes proper shutdown and error handling.
 */

package client;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;
import com.google.protobuf.Empty;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.StatusRuntimeException;
import io.grpc.stub.StreamObserver;
import solar.protos.SmartSolarServiceGrpc;
import solar.protos.GetDailyYieldRequest;
import solar.protos.GetDailyYieldResponse;
import solar.protos.RealTimeOutput;
import solar.protos.TradeRequest;
import solar.protos.TradeResponse;

public class SolarClient {
	
	private static Logger logger = Logger.getLogger(SolarClient.class.getName());
	
	//Host and port for connecting to the gRPC server
    static String host = "localhost";
    static int port = 50052;
    
    //Channel and stub declarations
    private static final ManagedChannel channel;
    private static final SmartSolarServiceGrpc.SmartSolarServiceBlockingStub blockingStub;
    private static final SmartSolarServiceGrpc.SmartSolarServiceStub asyncStub;  
    
    //Static initializer to set up channel and stubs
    static {
        channel = ManagedChannelBuilder.forAddress(host, port)
                .usePlaintext()
                .build();
        blockingStub = SmartSolarServiceGrpc.newBlockingStub(channel);
        asyncStub    = SmartSolarServiceGrpc.newStub(channel);
    }
    
    //Shutdown method
    public static void shutdown() {
    	channel.shutdown();
    	try {
    		if (!channel.awaitTermination(5,  TimeUnit.SECONDS)) {
    			channel.shutdownNow();
    		}
    	}catch(InterruptedException e) {
    		Thread.currentThread().interrupt();    	
    	}
    }
    
    //Unary RPC: Get daily yield
    public static GetDailyYieldResponse getDailyYield(String date) {
    	
    	//Build the request with date
    	GetDailyYieldRequest req = GetDailyYieldRequest.newBuilder()
    			.setDate(date)
    			.build();
    	try {
    		//Blocking call
    		return blockingStub.getDailyYield(req);
    	}catch(StatusRuntimeException e){
    		logger.warning("getDailyYield RPC failed: " + e.getStatus()); //Error handling
    		return null;
    	}
    }
    
    //Server streaming: real time energy output  
    public static Iterator<RealTimeOutput> streamRealTimeOutput() {
    	try {
    		//Return the iterator
    		return blockingStub.streamRealTimeOutput(Empty.getDefaultInstance());
    	}catch(StatusRuntimeException e) {
    		//Log error and return an empty iterator
            logger.warning("streamRealTimeOutput RPC failed: " + e.getStatus());
            return Collections.emptyIterator();
    	}
    }
    
    //Bidirectional streaming RPC: energy trade negotiation
    public static List<TradeResponse> negotiateTrades(List<TradeRequest> requests) throws InterruptedException {
    	CountDownLatch latch = new CountDownLatch(1);
    	List<TradeResponse> responses = new ArrayList<>();
    	
    	//Observer for server response
    	StreamObserver<TradeResponse> respObserver = new StreamObserver<TradeResponse>() {
    		@Override
    		public void onNext(TradeResponse resp) {
    			responses.add(resp); //Add each response to the list
    		}
    		@Override
    		public void onError(Throwable t) {
    			logger.warning("energyTradeNegotiation RPC failed: " + t.getMessage()); //Error handling
    			latch.countDown();
    		}
    		@Override
    		public void onCompleted() {
    			latch.countDown(); //Stream complete
    		}
    	};
    	
    	//Open the bidirectional stream
    	StreamObserver<TradeRequest> reqObserver = asyncStub.energyTradeNegotiation(respObserver);
    	
    	//Send requests
    	for(TradeRequest req : requests) {
    		reqObserver.onNext(req);
    	}
    	reqObserver.onCompleted();
    	
    	//Wait for response or timeout
    	if(!latch.await(10,  TimeUnit.SECONDS)) {
    		logger.warning("energyTradeNegotation RPC timeout");
    	}
    	return responses;
    }
}