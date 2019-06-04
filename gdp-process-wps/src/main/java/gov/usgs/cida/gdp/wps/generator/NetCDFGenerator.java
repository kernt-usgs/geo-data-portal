package gov.usgs.cida.gdp.wps.generator;

import gov.usgs.cida.gdp.wps.binding.CoverageFileBinding;
import gov.usgs.cida.gdp.wps.binding.NetCDFFileBinding;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import org.n52.wps.io.data.IData;
import org.n52.wps.io.datahandler.generator.AbstractGenerator;

/**
 *
 * @author tkunicki
 */
public class NetCDFGenerator extends AbstractGenerator {

    public NetCDFGenerator() {
        supportedIDataTypes.add(NetCDFFileBinding.class);
        supportedIDataTypes.add(CoverageFileBinding.class);
    }
    
    @Override
    public InputStream generateStream(IData data, String mimeType, String schema) throws IOException {
        if (data instanceof NetCDFFileBinding || data instanceof CoverageFileBinding) {
            Object payload = data.getPayload();
            if (payload instanceof File) {
                File payloadFile = (File) payload;
                return new FileInputStream(payloadFile);
            }
        }
        return null;
    }

}
