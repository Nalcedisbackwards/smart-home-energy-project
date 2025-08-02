package client;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;
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
import com.google.protobuf.Empty;

public class SolarClient {
	
	private static Logger logger = Logger.getLogger(SolarClient.class.getName());
	
	//gRPC channel and stubs
    static String host = "localhost";
    static int port = 50052;
    
    private static final ManagedChannel channel;
    private static final SmartSolarServiceGrpc.SmartSolarServiceBlockingStub blockingStub;
    private static final SmartSolarServiceGrpc.SmartSolarServiceStub asyncStub;  
    
    // Static initializer to set up channel and stubs
    static {
        channel = ManagedChannelBuilder.forAddress(host, port)
                .usePlaintext()
                .build();
        blockingStub = SmartSolarServiceGrpc.newBlockingStub(channel);
        asyncStub    = SmartSolarServiceGrpc.newStub(channel);
    }
    
  //shutdown method
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
    
    //Unary RPC: Get DailyYield
    public static GetDailyYieldResponse getDailyYield(String date) {
    	GetDailyYieldRequest req = GetDailyYieldRequest.newBuilder()
    			.setDate(date)
    			.build();
    	try {
    		return blockingStub.getDailyYield(req);
    	}catch(StatusRuntimeException e){
    		logger.warning("getDailyYield RPC failed: " + e.getStatus());
    		return null;
    	}
    }
    
    //Server streaming: RealTimeOutput
    public static List<RealTimeOutput> getRealTimeOutput(int numReadings) {
    	List<RealTimeOutput> outputs = new ArrayList<>();
    	try {
    		Iterator<RealTimeOutput> iter = blockingStub.streamRealTimeOutput(Empty.getDefaultInstance());
    		while(iter.hasNext() && outputs.size() < numReadings) {
    			outputs.add(iter.next());
    		}
    	}catch(StatusRuntimeException e) {
    		logger.warning("streamRealTimeOutput RPC failed: " + e.getStatus());
    	}
    	return outputs;
    }
    
    public static Iterator<RealTimeOutput> streamRealTimeOutput() {
    	return blockingStub.streamRealTimeOutput(Empty.getDefaultInstance());
    }
    
    //Bidirectional streaming RPC: EnergyTradeNegotiation
    public static List<TradeResponse> negotiateTrades(List<TradeRequest> requests) throws InterruptedException {
    	CountDownLatch latch = new CountDownLatch(1);
    	List<TradeResponse> responses = new ArrayList<>();
    	
    	//observer for server response
    	StreamObserver<TradeResponse> respObserver = new StreamObserver<TradeResponse>() {
    		@Override
    		public void onNext(TradeResponse resp) {
    			responses.add(resp);
    		}
    		@Override
    		public void onError(Throwable t) {
    			logger.warning("energyTradeNegotiation RPC failed: " + t.getMessage());
    			latch.countDown();
    		}
    		@Override
    		public void onCompleted() {
    			latch.countDown();
    		}
    	};
    	
    	//open the bidirectional stream
    	StreamObserver<TradeRequest> reqObserver = asyncStub.energyTradeNegotiation(respObserver);
    	
    	//send requests
    	for(TradeRequest req : requests) {
    		reqObserver.onNext(req);
    	}
    	reqObserver.onCompleted();
    	
    	//wait for response or timeout
    	if(!latch.await(10,  TimeUnit.SECONDS)) {
    		logger.warning("energyTradeNegotation RPC timeout");
    	}
    	return responses;
    }
}