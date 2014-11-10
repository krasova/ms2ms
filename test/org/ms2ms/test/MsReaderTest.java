package org.ms2ms.test;

import com.google.common.collect.Range;
import org.expasy.mzjava.core.io.ms.spectrum.MgfWriter;
import org.expasy.mzjava.core.io.ms.spectrum.MzxmlReader;
import org.expasy.mzjava.core.ms.peaklist.PeakList;
import org.expasy.mzjava.core.ms.spectrum.MsnSpectrum;
import org.junit.Test;
import org.ms2ms.data.ms.LcMsMsDataset;
import org.ms2ms.io.MsIO;
import org.ms2ms.io.MsReaders;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.Collection;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import static org.junit.Assert.assertEquals;

public class MsReaderTest extends TestAbstract
{
//  String root = "/Users/hliu/Desktop/Apps/2014/data/mzXML-centroid/";
  String root = "/media/data/test/mzXML/";

  // TODO: Need to modify or extend MzxmlReader to read only selected msLevel or RT range, etc
  // peak processing takes lots of time!

  @Test
  public void surveyMzXMLs() throws IOException
  {
    LcMsMsDataset test = new LcMsMsDataset("survey");
    test.setRawFilename(root+"20081129_Orbi6_NaNa_SA_FASP_blacktips_01.mzXML");

    test = MsReaders.surveyMzXML(test, null, 2);

    // write the examples out in MGF format
    Collection<Long> ids = test.getMzRtFileOffset().subset(495.2d, 495.35d, 0d, Double.MAX_VALUE);
    RandomAccessFile bin = test.getSpCacheFile(2);
    MgfWriter mgf = new MgfWriter(new File("/tmp/examples495_3.mgf"), PeakList.Precision.DOUBLE);
    for (Long id : ids)
    {
      bin.seek(id);
      MsnSpectrum ms = MsIO.read(bin, new MsnSpectrum());
      mgf.write(ms);
    }
    mgf.close(); bin.close();

    assertEquals(test.getMzRtFileOffset().size(), 36831);
  }
  @Test
  public void nextSpec() throws IOException
  {
    Logger.getLogger(MzxmlReader.class.getName()).setLevel(Level.SEVERE);

    File          data = new File(root+"20081129_Orbi6_NaNa_SA_FASP_blacktips_01.mzXML");
    MzxmlReader reader = MzxmlReader.newTolerantReader(data, PeakList.Precision.FLOAT);

    int counts=0;
    while (reader.hasNext())
    {
      MsnSpectrum spec = reader.next();
      if (++counts%100==0) System.out.print(".");
      if (counts%10000==0) System.out.println(counts);
    }
    reader.close();

    System.out.println("\n" + counts + " spectra imported");
  }
  @Test
  public void byRT()
  {
    List<MsnSpectrum> spectra = MsReaders.readMzXML(root + "20081129_Orbi6_NaNa_SA_FASP_blacktips_01.mzXML", Range.closed(20d, 21d), 2);

    System.out.println(spectra.size());
  }
}
