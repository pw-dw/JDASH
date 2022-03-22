package dash.client.handler;

import dash.client.DashClient;
import dash.client.fsm.DashClientEvent;
import dash.client.fsm.DashClientState;
import dash.client.handler.base.MessageType;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import stream.StreamConfigManager;
import util.fsm.StateManager;
import util.fsm.module.StateHandler;
import util.fsm.unit.StateUnit;
import util.module.FileManager;

import java.util.concurrent.TimeUnit;

public class DashVideoHttpClientHandler extends SimpleChannelInboundHandler<HttpObject> {

    private static final Logger logger = LoggerFactory.getLogger(DashVideoHttpClientHandler.class);

    private final TimeUnit timeUnit = TimeUnit.MICROSECONDS;

    private final DashClient dashClient;

    public DashVideoHttpClientHandler(DashClient dashClient) {
        this.dashClient = dashClient;
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        cause.printStackTrace();
        ctx.close();
    }

    @Override
    protected void messageReceived(ChannelHandlerContext channelHandlerContext, HttpObject httpObject) {
        if (dashClient == null) {
            logger.warn("[DashHttpClientHandler] DashClient is null. Fail to recv the message.");
            return;
        } else if (dashClient.isStopped()) {
            //logger.warn("[DashHttpClientHandler] DashClient({}) is already stopped. Fail to recv the message.", dashClient.getDashUnitId());
            return;
        }

        // RESPONSE
        if (httpObject instanceof HttpResponse) {
            HttpResponse response = (HttpResponse) httpObject;

            if (!response.status().equals(HttpResponseStatus.OK)) {
                int curVideoRetryCount = dashClient.incAndGetVideoRetryCount();
                if (curVideoRetryCount > StreamConfigManager.VIDEO_RETRY_LIMIT) {
                    logger.warn("[DashHttpClientHandler({})] [-] [VIDEO] !!! RECV NOT OK. DashClient will be stopped. (status={})", dashClient.getDashUnitId(), response.status());
                    dashClient.stop();
                    channelHandlerContext.close();
                    return;
                } else {
                    // SegmentDuration 의 절반 만큼(micro-sec) sleep
                    long segmentDuration = dashClient.getMpdManager().getVideoSegmentDuration(); // 1000000
                    if (segmentDuration > 0) {
                        try {
                            segmentDuration /= 2; // 500000
                            timeUnit.sleep(segmentDuration);
                            logger.trace("[DashHttpClientHandler({})] [VIDEO] Waiting... ({})", dashClient.getDashUnitId(), segmentDuration);
                        } catch (Exception e) {
                            //logger.warn("");
                        }
                    }

                    dashClient.sendHttpGetRequest(
                            FileManager.concatFilePath(
                                    dashClient.getSrcBasePath(),
                                    dashClient.getMpdManager().getVideoMediaSegmentName()
                            ),
                            MessageType.VIDEO
                    );

                    logger.warn("[DashHttpClientHandler({})] [VIDEO] Retrying... ({})", dashClient.getDashUnitId(), dashClient.getMpdManager().getVideoMediaSegmentName());
                }
            }

            if (logger.isTraceEnabled()) {
                logger.trace("[DashHttpClientHandler({})] > STATUS: {}", dashClient.getDashUnitId(), response.status());
                logger.trace("> VERSION: {}", response.protocolVersion());

                if (!response.headers().isEmpty()) {
                    for (CharSequence name : response.headers().names()) {
                        for (CharSequence value : response.headers().getAll(name)) {
                            logger.trace("[DashHttpClientHandler({})] > HEADER: {} = {}", dashClient.getDashUnitId(), name, value);
                        }
                    }
                }

                if (HttpHeaderUtil.isTransferEncodingChunked(response)) {
                    logger.trace("[DashHttpClientHandler({})] > CHUNKED CONTENT {", dashClient.getDashUnitId());
                } else {
                    logger.trace("[DashHttpClientHandler({})] > CONTENT {", dashClient.getDashUnitId());
                }
            }
        }

        // CONTENT
        if (httpObject instanceof HttpContent) {
            HttpContent httpContent = (HttpContent) httpObject;
            ByteBuf buf = httpContent.content();
            if (buf == null) {
                logger.warn("[ProcessClientChannelHandler] DatagramPacket's content is null.");
                return;
            }

            int readBytes = buf.readableBytes();
            if (buf.readableBytes() <= 0) {
                logger.warn("[ProcessClientChannelHandler] Message is null.");
                return;
            }

            byte[] data = new byte[readBytes];
            buf.getBytes(0, data);

            StateManager stateManager = dashClient.getDashClientFsmManager().getStateManager();
            StateHandler stateHandler = dashClient.getDashClientFsmManager().getStateManager().getStateHandler(DashClientState.NAME);
            StateUnit stateUnit = stateManager.getStateUnit(dashClient.getDashClientStateUnitId());
            String curState = stateUnit.getCurState();
            switch (curState) {
                case DashClientState.MPD_DONE:
                    dashClient.getMpdManager().makeInitSegment(dashClient.getTargetVideoInitSegPath(), data);
                    break;
                case DashClientState.INIT_SEG_DONE:
                    String targetVideoMediaSegPath = FileManager.concatFilePath(
                            dashClient.getTargetBasePath(),
                            dashClient.getMpdManager().getVideoMediaSegmentName()
                    );
                    dashClient.getMpdManager().makeMediaSegment(targetVideoMediaSegPath, data);
                    break;
            }

            logger.trace("[DashHttpClientHandler({})] [VIDEO] {}", dashClient.getDashUnitId(), data);
            if (httpContent instanceof LastHttpContent) {
                switch (curState) {
                    case DashClientState.MPD_DONE:
                        stateHandler.fire(DashClientEvent.GET_INIT_SEG, stateUnit);
                        break;
                    case DashClientState.INIT_SEG_DONE:
                        String prevMediaSegmentName = dashClient.getMpdManager().getVideoMediaSegmentName();
                        dashClient.getMpdManager().incAndGetVideoSegmentSeqNum();
                        String curMediaSegmentName = dashClient.getMpdManager().getVideoMediaSegmentName();
                        logger.trace("[DashHttpClientHandler({})] [+] [VIDEO] MediaSegment is changed. ([{}] > [{}])", dashClient.getDashUnitId(), prevMediaSegmentName, curMediaSegmentName);

                        // SegmentDuration 만큼(micro-sec) sleep
                        long segmentDuration = dashClient.getMpdManager().getVideoSegmentDuration(); // 1000000
                        /*double availabilityTimeOffset = dashClient.getMpdManager().getAvailabilityTimeOffset(); // 0.8
                        if (availabilityTimeOffset > 0) {
                            segmentDuration *= availabilityTimeOffset; // 800000
                        }*/
                        if (segmentDuration > 0) {
                            try {
                                timeUnit.sleep(segmentDuration);
                                logger.trace("[DashHttpClientHandler({})] [VIDEO] Waiting... ({})", dashClient.getDashUnitId(), segmentDuration);
                            } catch (Exception e) {
                                //logger.warn("");
                            }
                        }

                        dashClient.sendHttpGetRequest(
                                FileManager.concatFilePath(
                                        dashClient.getSrcBasePath(),
                                        curMediaSegmentName
                                ),
                                MessageType.VIDEO
                        );
                        break;
                }

                logger.trace("[DashHttpClientHandler({})] } END OF CONTENT <", dashClient.getDashUnitId());
            }
        }
    }

}