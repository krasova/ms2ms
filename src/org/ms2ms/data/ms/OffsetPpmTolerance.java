package org.ms2ms.data.ms;

import com.google.common.collect.Range;
import org.expasy.mzjava.core.ms.PpmTolerance;

/**
 * Created by yuw on 8/6/16.
 */
public class OffsetPpmTolerance extends PpmTolerance
{
  private double mScale=1d, mOffset=0d, mTol=0d, mTransitionMass=0, mOffsetSlope=0, mTolSlope=0;

  public OffsetPpmTolerance() { super(0d); }
  public OffsetPpmTolerance(double tol) { super(tol); mTol=tol; }
  public OffsetPpmTolerance(double tol, double offset)
  {
    super(tol); mOffset=offset; mTol=tol;
  }

  public OffsetPpmTolerance setOffsetParams(double transition, double intercept, double slope)
  {
    mTransitionMass=transition; mOffsetSlope=slope; mOffset=intercept; return this;
  }
  public OffsetPpmTolerance setTolParams(double intercept, double slope)
  {
    mTolSlope=slope; mTol=intercept; return this;
  }
  public double getOffset(double m)
  {
    return m<mTransitionMass?(mOffset+mOffsetSlope*m):(mOffset);
  }
//  @Deprecated
//  public double getPpmTol() { return mTol*mScale; }
  public double getPpmTol(double m) { return (mTol+mTolSlope*m)*mScale; }
//  public boolean isWithinByPPM(double s) { return Math.abs(s)<=mTol*mScale; }

  public OffsetPpmTolerance scale( double s) { mScale =s; return this; }
  public OffsetPpmTolerance offset(double s) { mOffset=s; return this; }

  @Override
  public Location check(double expected, double actual)
  {
    if      (actual < getMin(expected)) return Location.SMALLER;
    else if (actual > getMax(expected)) return Location.LARGER;
    else                                return Location.WITHIN;
  }

//  @Deprecated
//  public double getOffset() { return mOffset; }
  public Range<Double> getBoundary(double mz) { return Range.closed(getMin(mz), getMax(mz)); }
  // because of the directionality of the offset, we have to specify the direction as well.
//  public double[] toExpectedBoundary(double mz) { return new double[] {mz-calcError(mz)+calcOffset(mz), mz+calcError(mz)+calcOffset(mz)}; }
  public double[] toActualBoundary(  double mz) { return new double[] {mz-calcError(mz)-calcOffset(mz), mz+calcError(mz)-calcOffset(mz)}; }
  public float[]  toExpectedBoundary(float mz)  { return new float[] {(float )(mz-calcError(mz)+calcOffset(mz)), (float )(mz+calcError(mz)+calcOffset(mz))}; }
//  public float[]  toActualBoundary(  float mz)  { return new float[] {(float )(mz-calcError(mz)-calcOffset(mz)), (float )(mz+calcError(mz)-calcOffset(mz))}; }

  @Override
  public boolean withinTolerance(double expected, double actual) { return actual >= getMin(expected) && actual <= getMax(expected); }

  @Override
  public double getMin(double mz) { return  mz-calcError(mz)+calcOffset(mz); }
  @Override
  public double getMax(double mz) { return  mz+calcError(mz)+calcOffset(mz); }

  public OffsetPpmTolerance clone()
  {
    return new OffsetPpmTolerance().setOffsetParams(mTransitionMass, mOffset, mOffsetSlope).setTolParams(mTol, mTolSlope);
  }
  private double calcError( double expectedMass) { return expectedMass * (getPpmTol(expectedMass)*mScale/1000000d); }
  private double calcOffset(double expectedMass) { return expectedMass * (getOffset(expectedMass)/1000000d); }
}
