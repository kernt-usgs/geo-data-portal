package gov.usgs.cida.gdp.wps.generator;

import gov.usgs.cida.gdp.wps.binding.CoverageFileBinding;
import gov.usgs.cida.gdp.wps.binding.ZipFileBinding;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import org.n52.wps.io.data.GenericFileData;
import org.n52.wps.io.data.IData;
import org.n52.wps.io.data.binding.complex.GenericFileDataBinding;
import org.n52.wps.io.datahandler.generator.AbstractGenerator;

/**
 *
 * @author tkunicki
 */
public class ZipGenerator extends AbstractGenerator {

    public ZipGenerator() {
        supportedIDataTypes.add(ZipFileBinding.class);
        // added this here rather than make R binding be forced to ZipFileBinding for hint=zip
        supportedIDataTypes.add(GenericFileDataBinding.class);
        // added this to support zipped geotiff collections
        supportedIDataTypes.add(CoverageFileBinding.class);
    }
    
    @Override
    public InputStream generateStream(IData data, String mimeType, String schema) throws IOException {
        if (data instanceof ZipFileBinding ||
            data instanceof GenericFileDataBinding ||
            data instanceof CoverageFileBinding) {
            Object payload = data.getPayload();
            if (payload instanceof File) {
                File payloadFile = (File) payload;
                return new FileInputStream(payloadFile);
            } else if (payload instanceof GenericFileData) {
                GenericFileData payloadFile = (GenericFileData) payload;
                return payloadFile.getDataStream();
            }
        }
        return null;
    }

}
