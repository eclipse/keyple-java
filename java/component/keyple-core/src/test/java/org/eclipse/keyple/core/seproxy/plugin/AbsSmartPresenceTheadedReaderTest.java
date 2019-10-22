package org.eclipse.keyple.core.seproxy.plugin;

import org.eclipse.keyple.core.CoreBaseTest;
import org.eclipse.keyple.core.seproxy.event.ObservableReader;
import org.eclipse.keyple.core.seproxy.event.ReaderEvent;
import org.eclipse.keyple.core.seproxy.exception.KeypleIOReaderException;
import org.eclipse.keyple.core.seproxy.exception.KeypleReaderException;
import org.eclipse.keyple.core.seproxy.exception.NoStackTraceThrowable;
import org.eclipse.keyple.core.util.ByteArrayUtil;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.mockito.Mockito;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.eclipse.keyple.core.seproxy.plugin.AbstractObservableLocalReader.MonitoringState.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyByte;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;

/*
 * test the feature of SmartPresence methods
 */
@RunWith(Parameterized.class)
public class AbsSmartPresenceTheadedReaderTest extends CoreBaseTest {

    private static final Logger logger = LoggerFactory.getLogger(AbsSmartPresenceTheadedReaderTest.class);


    final String PLUGIN_NAME = "AbsSmartPresenceTheadedReaderTestP";
    final String READER_NAME = "AbsSmartPresenceTheadedReaderTest";

    BlankSmartPresenceTheadedReader r;

    //Execute tests 10 times
    @Parameterized.Parameters
    public static Object[][] data() {
        int x = 0;
        return new Object[x][0];
    }


    @Before
    public void setUp() throws KeypleReaderException {
        logger.info("------------------------------");
        logger.info("Test {}", name.getMethodName() + "");
        logger.info("------------------------------");

       r = getSmartSpy(PLUGIN_NAME, READER_NAME);
    }

    /*
     */
    @After
    public void tearDown() throws Throwable {
            r.clearObservers();
            r.finalize();
            r = null;

    }

    @Test
    public void startRemovalSequence() throws Exception{

        //SE matched
        doReturn(true).when(r).processSeInserted();

        r.addObserver(getObs());
        Thread.sleep(100);

        r.startRemovalSequence();
        Thread.sleep(100);

        //does nothing
        Assert.assertEquals(WAIT_FOR_START_DETECTION, r.getMonitoringState());
    }

    @Test
    public void startRemovalSequence_CONTINUE() throws Exception, NoStackTraceThrowable {

        //SE matched
        doReturn(true).when(r).processSeInserted();
        //use mocked BlankSmartPresenceTheadedReader methods

        r.addObserver(getObs());
        r.startSeDetection(ObservableReader.PollingMode.CONTINUE);//WAIT_FOR_SE_INSERTION
        Thread.sleep(100);

        r.startRemovalSequence();
        Thread.sleep(100);

        Assert.assertEquals(WAIT_FOR_SE_INSERTION, r.getMonitoringState());
    }

    @Test
    public void startRemovalSequence_noping_STOP() throws Exception, NoStackTraceThrowable {

        //SE matched
        doReturn(true).when(r).processSeInserted();
        doReturn(false).when(r).isSePresentPing();

        r.addObserver(getObs());
        r.startSeDetection(ObservableReader.PollingMode.STOP);
        Thread.sleep(100);

        r.startRemovalSequence();
        Thread.sleep(100);

        Assert.assertEquals(WAIT_FOR_SE_INSERTION, r.getMonitoringState());
    }


    @Test
    public void startRemovalSequence_ping_STOP() throws Exception, NoStackTraceThrowable {

        //SE matched
        doReturn(true).when(r).processSeInserted();
//        doReturn(true).when(r).isSePresentPing();
        doReturn(true).when(r).isSePresent();

        r.addObserver(getObs());
        r.startSeDetection(ObservableReader.PollingMode.STOP);
        Thread.sleep(100);

        r.startRemovalSequence();
        Thread.sleep(100);

        Assert.assertEquals(WAIT_FOR_START_DETECTION, r.getMonitoringState());
    }

    /*
     * Helpers
     */

    static public BlankSmartPresenceTheadedReader getSmartSpy(String pluginName, String readerName) throws KeypleReaderException {
        BlankSmartPresenceTheadedReader r =  Mockito.spy(new BlankSmartPresenceTheadedReader(pluginName,readerName,1));
        return  r;
    }


    static public ObservableReader.ReaderObserver getObs(){
        return  new ObservableReader.ReaderObserver() {@Override  public void update(ReaderEvent event) {}};
    }

    static public ObservableReader.ReaderObserver countDownOnTimeout(final CountDownLatch lock){
        return  new ObservableReader.ReaderObserver() {@Override  public void update(ReaderEvent event) {
            if(ReaderEvent.EventType.TIMEOUT_ERROR.equals(event.getEventType())){
                lock.countDown();
            }
        }};
    }

}