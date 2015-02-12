package org.ms2ms.test.ms;

import com.google.common.collect.Multimap;
import org.expasy.mzjava.core.ms.spectrum.MsnSpectrum;
import org.junit.Test;
import org.ms2ms.alg.Spectra;
import org.ms2ms.data.ms.MaxQuant;
import org.ms2ms.io.MsIO;
import org.ms2ms.r.Dataframe;
import org.ms2ms.test.TestAbstract;

import java.io.RandomAccessFile;
import java.util.Collection;

/**
 * ** Copyright 2014-2015 ms2ms.org
 * <p/>
 * Description:
 * <p/>
 * Author: wyu
 * Date:   11/20/14
 */
public class BayesGuruTest extends TestAbstract
{
  @Test
  public void preclusterByMSeq() throws Exception
  {
    String[] selected = { MaxQuant.V_MZ,MaxQuant.V_RT,MaxQuant.V_TIC,MaxQuant.V_MSEQ,MaxQuant.V_SEQ,MaxQuant.V_OFFSET,MaxQuant.V_Z };
    Dataframe msms = Dataframe.readtable("/media/data/maxquant/20081129_Orbi6_NaNa_SA_FASP_out/combined/txt/composite_scans.txt", selected,'\t', true).setTitle("msms");

    Multimap<String, String> seq_row   = msms.factorize(MaxQuant.V_MSEQ);

    RandomAccessFile bin = new RandomAccessFile("/media/data/test/mzXML/cache173190685179316.ms2", "r");
    // starting from the most intense scan
    int counts=0;
    for (String seq : seq_row.keySet())
    {
      if (seq_row.get(seq).size()<36) continue;

      Collection<String> slice = seq_row.get(seq);
      Multimap<Integer, MsnSpectrum> z_spec = Spectra.toChargePeakList(MsIO.readSpectra(bin, msms.getLongCol(MaxQuant.V_OFFSET, slice)));
      for (Integer z : z_spec.keySet())
      {
        String fname = "/media/data/tmp/examples_"+z_spec.get(z).size()+"_"+seq+"_z"+z+".ms2";
        MsIO.writeSpectra(fname, z_spec.get(z));
        System.out.println("Writing to " + fname);
      }
      if (++counts>5) return;
    }
    System.out.println();
  }
}