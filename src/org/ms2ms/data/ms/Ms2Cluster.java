package org.ms2ms.data.ms;

import org.expasy.mzjava.core.ms.Tolerance;
import org.expasy.mzjava.core.ms.peaklist.Peak;
import org.expasy.mzjava.core.ms.spectrum.MsnSpectrum;
import org.ms2ms.algo.Peaks;
import org.ms2ms.algo.Similarity;
import org.ms2ms.algo.Spectra;
import org.ms2ms.data.Binary;
import org.ms2ms.io.BufferedRandomAccessFile;
import org.ms2ms.io.MsIO;
import org.ms2ms.math.Stats;
import org.ms2ms.utils.IOs;
import org.ms2ms.utils.Strs;
import org.ms2ms.utils.Tools;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.util.*;

public class Ms2Cluster implements Comparable<Ms2Cluster>, Binary
{
  public enum NodeType { REF, MSMS, NONE };

  private NodeType mType = NodeType.MSMS;
  private float mMz, mRT, mImpurity=-1;
  private int mByMz=0, mByMzRT=0, mByMzRtFrag=0, mCharge=0;
  private String mName="", mID="", mMajority=null;

  private MsnSpectrum mMaster; // a composite spectrum to represent the cluster

  private Ms2Pointer mHead;
  private Collection<Ms2Pointer> mMembers = new TreeSet<>(), mCandidates = new TreeSet<>(); // actual or possible members of the cluster


  public Ms2Cluster() { super(); }
  public Ms2Cluster(String s) { super(); mName=s; }
  public Ms2Cluster(String n, String id, NodeType t) { super(); mName=n; mID=id; mType=t; }
  public Ms2Cluster(Ms2Pointer s) { super(); mHead=s; }

  public Collection<Ms2Pointer> getCandidates() { return mCandidates; }
  public Collection<Ms2Pointer> getMembers()    { return mMembers; }
  public MsnSpectrum            getMaster()     { return mMaster; }
  public Ms2Pointer             getHead()       { return mHead; }

  public boolean isType(NodeType t) { return mType.equals(t); }

  public int    getCandidateSize() { return mCandidates!=null?mCandidates.size():0; }; // the head is already a part of the candidates and members
  public int    size()           { return mMembers!=null?mMembers.size():0; }; // the head is already a part of the candidates and members
  public int    getNbyMz()       { return mByMz; }
  public int    getNbyMzRT()     { return mByMzRT; }
  public int    getCharge()      { return mCharge; }
  public float  getMz()          { return mMz; }
  public float  getRT()          { return mRT; }
  public float  getImpurity()    { return mImpurity; }
  public int    getNbyMzRtFrag() { return mByMzRtFrag; }
  public String getName()        { return mName; }
  public String getID()          { return mID; }
  public String getMajority()    { return mMajority; }

  public Ms2Cluster setType(NodeType   s) { mType      =s; return this; }
  public Ms2Cluster setHead(Ms2Pointer s) { mHead      =s; return this; }
  public Ms2Cluster setNbyMz(      int s) { mByMz      =s; return this; }
  public Ms2Cluster setNbyMzRt(    int s) { mByMzRT    =s; return this; }
  public Ms2Cluster setNbyMzRtFrag(int s) { mByMzRtFrag=s; return this; }
  public Ms2Cluster setName(    String s) { mName      =s; return this; }
  public Ms2Cluster setID(      String s) { mID        =s; return this; }
  public Ms2Cluster setMajorityID(String s) { mMajority  =s; return this; }
  public Ms2Cluster setMz(       float s) { mMz        =s; return this; }
  public Ms2Cluster setRT(       float s) { mRT        =s; return this; }
  public Ms2Cluster setImpurity( float s) { mImpurity  =s; return this; }

  public Ms2Cluster addCandidate(Ms2Pointer s) { if (s!=null) mCandidates.add(s); return this; }
  public Ms2Cluster addMember(   Ms2Pointer s) { if (s!=null) mMembers.add(s); return this; }

  public int getCandidateRemain() { return (mCandidates!=null?mCandidates.size():0)-(mMembers!=null?mMembers.size():0); }
  public boolean contains(Ms2Pointer p)
  {
    if (Tools.equals(p, mHead) || Tools.contains(mCandidates, p)) return true;
    return false;
  }
  public Ms2Cluster setMaster(BufferedRandomAccessFile bin) throws IOException
  {
    if (size()<=1) mMaster = MsIO.readSpectrumIdentifier(bin, new MsnSpectrum(), getHead().pointer);
    return this;
  }
  public Ms2Cluster resetMembers()
  {
    if (mMembers!=null) mMembers.clear(); else mMembers = new TreeSet<>();
    return this;
  }
  public Ms2Cluster resetCandidates()
  {
    if (mCandidates!=null) mCandidates.clear(); else mCandidates = new TreeSet<>();
    return this;
  }
  /// start the method section  ///

  // from the HEAD to the candidates at high cutoff
  public Ms2Cluster cluster(Map<Ms2Pointer, MsnSpectrum> spectra, Float lowmass, OffsetPpmTolerance tol, double min_dp, int miss_index)
  {
    // quit if the master or input are not present!
    if (mHead==null || !Tools.isSet(spectra)) return null;

    // setting the seed spectrum
    MsnSpectrum HEAD = (mMaster!=null?mMaster:spectra.get(mHead));
    // get the index of the HEAD spectrum
    List<Peak> index = Similarity.index(Spectra.toListOfPeaks(HEAD, lowmass), 7, 1, 5, 0);

    // the collections
    if (mMembers!=null) mMembers.clear(); else mMembers = new ArrayList<>();
    Collection<MsnSpectrum> members = new ArrayList<>();
    // setup the master first
    List<Peak> head = Spectra.toListOfPeaks(HEAD, lowmass);
    double delta = tol.calcError(500);
    for (Ms2Pointer member : mCandidates)
    {
      MsnSpectrum scan = spectra.get(member);
      if (scan!=null)
      {
        // calc the forward and backward DPs and choose the smallest
        List<Peak> pks = Spectra.toListOfPeaks(scan,lowmass);
        // make sure a min number of the index peaks are found
//        if (index.size()-Peaks.overlap_counts(pks, index, delta, true)>miss_index) continue;

        member.dp=(float )Similarity.bidirectional_dp(head, pks, tol, true, true, true);
        // now the matching probability
        member.prob = (float )Similarity.similarity_hg(head, pks, delta);

        if (member.dp>=min_dp)
        {
          mMembers.add(member);
          members.add(spectra.get(member));
        }
      }
    }
    if (members.size()>1)
    {
      mMaster = Spectra.accumulate(HEAD, tol, 0.5f, members);
      mMz     = (float )mMaster.getPrecursor().getMz();
      mHead.cluster=this;
    }

    // remove the local objects
    head = (List )Tools.dispose(head);
    members = Tools.dispose(members);

    return this;
  }
  public Ms2Cluster calcMz()
  {
    if (Tools.isSet(mMembers))
    {
      Collection<Double> ms = new ArrayList<>(); double z=0;
      for (Ms2Pointer p : mMembers) { ms.add((double )p.mz); z+=p.z; }
      mMz = (float )Stats.mean(ms); mCharge=(int )Math.round(z);
    }
    return this;
  }
  // trim away the members in the cluster by the matching probability to the 'center'.
  public Ms2Cluster trimByMatchProb(Map<Ms2Pointer, MsnSpectrum> spectra, int regions, double cut)
  {
    // quit if the master or input are not present!
    if (mMaster==null || !Tools.isSet(spectra)) return null;

    // grab the index from our master first
    List<Peak> master = Similarity.index(Spectra.toListOfPeaks(mMaster), regions, 1, 5, 0);
    long bins = (long )(Math.log(2)/Math.log(1d+5E-6)), npks = master.size();

    Iterator<Ms2Pointer> itr = mCandidates.iterator();
    while (itr.hasNext())
    {
      MsnSpectrum scan = spectra.get(itr.next());
      if (scan!=null)
      {
        List<Peak> member = Similarity.index(Spectra.toListOfPeaks(scan), regions, 1, 5, 0);
        int overlap = Peaks.overlap_counts(master, member, 0.01, true);

        double prob = Stats.hypergeom(overlap, npks, member.size(), bins);

        // check the exit condition
        if (-1d*prob<cut) itr.remove();
        // clean up the objects
        member = (List )Tools.dispose(member);
      }
    }

    return this;
  }
  // trim the matching candidates by matching probability
  public Ms2Cluster trimByDotP(Map<Ms2Pointer, MsnSpectrum> spectra, Tolerance tol, double min_dp)
  {
    // quit if the master or input are not present!
    if (mMaster==null || !Tools.isSet(spectra)) return null;

    // setup the master first
    List<Peak>        master = Spectra.toListOfPeaks(mMaster);
    Iterator<Ms2Pointer> itr = mCandidates.iterator();
    while (itr.hasNext())
    {
      MsnSpectrum scan = spectra.get(itr.next());
      if (scan!=null && Similarity.dp(master, Spectra.toListOfPeaks(scan), tol, true, true)<min_dp) itr.remove();
    }

    return this;
  }
  @Override
  public int hashCode()
  {
    int hc = mHead!=null?mHead.hashCode():0;
    if (mName!=null) hc+=mName.hashCode();

    hc += mByMz+mByMzRT+mByMzRtFrag;

    if (Tools.isSet(mCandidates))
      for (Ms2Pointer p : mCandidates) hc+=p.hashCode();

    if (Tools.isSet(mMembers))
      for (Ms2Pointer p : mMembers) hc+=p.hashCode();

    return hc;
  }
  @Override
  public String toString()
  {
    return mType+(Strs.isSet(getName())?"::"+getName():"")+(size()!=0?"#"+size():"")+(getCandidateSize()!=0?"$"+
        getCandidateSize():"")+(mHead!=null?mHead.toString():(getMembers()!=null?Tools.front(getMembers()).toString():""));
  }
  @Override
  public int compareTo(Ms2Cluster o)
  {
    return Integer.compare(hashCode(), o.hashCode());
  }

  @Override
  public void write(DataOutput ds) throws IOException
  {
    IOs.write(ds, mType.name());
    IOs.write(ds, mMz);
    IOs.write(ds, mRT);
    IOs.write(ds, mByMz);
    IOs.write(ds, mByMzRT);
    IOs.write(ds, mByMzRtFrag);
    IOs.write(ds, mCharge);
    IOs.write(ds, mName);
    IOs.write(ds, mID);
    IOs.write(ds, mHead);

    IOs.write(ds, mMembers);
    IOs.write(ds, mCandidates);

    // write the size of the peaks
    IOs.write(ds, mMaster!=null?mMaster.size():0);
    if (mMaster!=null) MsIO.write(ds, mMaster);
  }

  @Override
  public void read(DataInput ds) throws IOException
  {
    mType       = NodeType.valueOf(IOs.read(ds, ""));
    mMz         = IOs.read(ds, 0f);
    mRT         = IOs.read(ds, 0f);
    mByMz       = IOs.read(ds, 0);
    mByMzRT     = IOs.read(ds, 0);
    mByMzRtFrag = IOs.read(ds, 0);
    mCharge     = IOs.read(ds, 0);

    mName       = IOs.read(ds, "");
    mID         = IOs.read(ds, "");
    mHead       = IOs.read(ds, new Ms2Pointer());

    mMembers    = IOs.readList(ds, Ms2Pointer.class);
    mCandidates = IOs.readList(ds, Ms2Pointer.class);

    int npks = IOs.read(ds, 0);
    if (npks>0) mMaster = MsIO.readSpectrumIdentifier(ds, new MsnSpectrum());

    if (!Strs.isSet(mID)) mID = toString();
  }
}
