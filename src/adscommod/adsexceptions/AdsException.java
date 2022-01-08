package adsexceptions;

public class AdsException extends RuntimeException {
  private long errId;

  public AdsException(long errId) { this.errId = errId;}
  public long getErrId() { return errId;}
  @Override
  public String toString() {return "0x" + Long.toHexString(errId);}
}
