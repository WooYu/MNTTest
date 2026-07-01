package com.mnatool.yunjutongprobe.storage;

import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import com.mnatool.yunjutongprobe.metrics.ProbeSample;


public class ProbeCsvReaderTest {
    @Test
    public void parseRowRestoresReceivedSample() {
        String line = "\"run-1\",\"UDP\",\"基线\",\"10.0.0.1\",9000,\"\",\"\","
                + "3,1000,64,true,false,12.500,0,0,false,false,true,\"\"";
        ProbeSample sample = ProbeCsvReader.parseRow(line);
        assertNotNull(sample);
        assertEquals(3, sample.seq);
        assertTrue(sample.received());
        assertEquals(12.5, sample.rttMs(), 0.001);
    }

    @Test
    public void parseRowRestoresLostSample() {
        String line = "\"run-1\",\"UDP\",\"基线\",\"10.0.0.1\",9000,\"\",\"\","
                + "5,2000,64,false,true,0.000,0,0,false,false,false,\"timeout\"";
        ProbeSample sample = ProbeCsvReader.parseRow(line);
        assertNotNull(sample);
        assertEquals(5, sample.seq);
        assertFalse(sample.received());
        assertEquals("timeout", sample.error);
    }

    @Test
    public void parseHandlesQuotedCommasInFields() {
        String[] fields = ProbeCsvReader.splitCsvLine("\"a,b\",\"c\"");
        assertEquals(2, fields.length);
        assertEquals("a,b", fields[0]);
        assertEquals("c", fields[1]);
    }

    @Test
    public void parseSortsBySequence() {
        String csv = "run_id,protocol,mode_tag,host,port,mqtt_publish_topic,mqtt_subscribe_topic,"
                + "seq,client_send_ms,packet_bytes,received,timeout,rtt_ms,server_recv_ns,server_send_ns,"
                + "duplicate,reordered,vpn_active,error\n"
                + "\"r\",\"UDP\",\"m\",\"h\",1,\"\",\"\",2,100,64,true,false,10.000,0,0,false,false,false,\"\"\n"
                + "\"r\",\"UDP\",\"m\",\"h\",1,\"\",\"\",1,50,64,true,false,5.000,0,0,false,false,false,\"\"\n";
        List<ProbeSample> samples = ProbeCsvReader.parse(csv);
        assertEquals(2, samples.size());
        assertEquals(1, samples.get(0).seq);
        assertEquals(2, samples.get(1).seq);
    }

    @Test
    public void parseReturnsEmptyWhenLinesWereConcatenated() {
        String broken = "run_id,protocol,mode_tag,host,port,mqtt_publish_topic,mqtt_subscribe_topic,"
                + "seq,client_send_ms,packet_bytes,received,timeout,rtt_ms,server_recv_ns,server_send_ns,"
                + "duplicate,reordered,vpn_active,error"
                + "\"r\",\"UDP\",\"m\",\"h\",1,\"\",\"\",1,50,64,true,false,5.000,0,0,false,false,false,\"\"";
        assertTrue(ProbeCsvReader.parse(broken).isEmpty());
    }

    @Test
    public void parseRestoresReceivedSampleWithZeroRtt() {
        String line = "\"run-1\",\"UDP\",\"基线\",\"10.0.0.1\",9000,\"\",\"\","
                + "1,1000,64,true,false,0.000,0,0,false,false,true,\"\"";
        ProbeSample sample = ProbeCsvReader.parseRow(line);
        assertNotNull(sample);
        assertTrue(sample.received());
    }
}
