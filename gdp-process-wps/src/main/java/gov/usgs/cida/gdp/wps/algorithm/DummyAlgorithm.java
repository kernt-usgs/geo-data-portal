package gov.usgs.cida.gdp.wps.algorithm;

import org.n52.wps.algorithm.annotation.Algorithm;
import org.n52.wps.algorithm.annotation.Execute;
import org.n52.wps.algorithm.annotation.LiteralDataInput;
import org.n52.wps.algorithm.annotation.LiteralDataOutput;
import org.n52.wps.server.AbstractAnnotatedAlgorithm;

/**
 *
 * @author smlarson
 */
@Algorithm(
        version = "1.0.0",
        title = "DummyAlgorithm",
        abstrakt = "This is used to test the throttle queue.")
public class DummyAlgorithm extends AbstractAnnotatedAlgorithm {

    private String dataSetUri;
    private long sleepTime;
    private String output;


    @LiteralDataInput(
            identifier = "DATASET_URI",
            title = "DummyAlgorithm",
            abstrakt = "")
     public void setDataSetUri(String datasetUri) {
        this.dataSetUri = datasetUri;
}

//output echo out the input mimetype text/plain
@LiteralDataOutput(
        identifier = "OUTPUT",
        title = "Output File",
        abstrakt = "A string message.")
        //binding = CSVFileBinding.class)  //mimetype="text/plain
        public String getOutput() {
		return output;
	}  
    
    @LiteralDataInput(
                identifier = "DUMMY_ALGO_SLEEP",
                title = "DummyAlgorithm",
                abstrakt = "")
        public void setSleepTime(long sleepTimeMillis) {
        this.sleepTime = sleepTimeMillis;
    }
        
    @Execute
        public void process() {
        try {
            //sleep for the amount of time designated in the passed in value

            Thread.sleep(this.sleepTime);
            output = "Dummy Algorithm is done.";
        } catch (InterruptedException ex) {
            addError("General Error: " + ex.getMessage());
        }
    }
}
