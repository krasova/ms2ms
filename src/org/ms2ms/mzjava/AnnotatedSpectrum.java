package org.ms2ms.mzjava;

import org.expasy.mzjava.proteomics.mol.AminoAcid;
import org.expasy.mzjava.proteomics.mol.Peptide;
import org.expasy.mzjava.proteomics.ms.spectrum.LibrarySpectrum;

import java.util.HashMap;
import java.util.Map;

/**
 * Created by wyu on 4/28/14.
 */
public class AnnotatedSpectrum extends LibrarySpectrum
{
  public static final String SCR_MIMSL       = "MIMSL";
  public static final String SCR_MIMSL_DELTA = "Delta MIMSL";

  private int ion_indiced, ion_queried;
  private Map<String, Double> scoreMap = new HashMap<String, Double>();

  public AnnotatedSpectrum()     { super(new Peptide(AminoAcid.X)); }
  public AnnotatedSpectrum(Peptide peptide)     { super(peptide); }
  public AnnotatedSpectrum(LibrarySpectrum src) { super(src); }

  public AnnotatedSpectrum(Peptide peptide, Precision precision)
  {
    super(peptide, precision);
  }
  public AnnotatedSpectrum(Peptide peptide, int initialCapacity, Precision precision)
  {
    super(peptide, initialCapacity, precision);
  }

  public AnnotatedSpectrum setScore(String name, Double score) { scoreMap.put(name, score); return this; }
  public double            getScore(String name)               { return scoreMap.get(name); }
  public AnnotatedSpectrum setIonIndexed(int s)                { ion_indiced = s;; return this; }
  public int               getIonIndexed()                     { return ion_indiced; }
  public AnnotatedSpectrum setIonQueried(int s)                { ion_queried = s;; return this; }
  public int               getIonQueried()                     { return ion_queried; }

}
