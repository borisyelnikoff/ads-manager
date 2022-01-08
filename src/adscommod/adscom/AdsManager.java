package adscom;

import de.beckhoff.jni.Convert;
import de.beckhoff.jni.JNIByteBuffer;
import de.beckhoff.jni.tcads.AmsAddr;
import de.beckhoff.jni.tcads.AdsVersion;
import de.beckhoff.jni.tcads.AdsCallDllFunction;
import de.beckhoff.jni.tcads.AdsState;
import de.beckhoff.jni.tcads.AdsDevName;
import java.nio.ByteBuffer;
import adsexceptions.*;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.BufferedReader;

/**
* The class for handling PLC<->OS communication through ADS protocol.
* Only one instance of this class is allowed (Singleton class type).
* Class is thread-safe.
* @author Bart Zawada
* @version 1.0
*/
public class AdsManager {
  private static boolean alive;
  private static AdsManager objRef;
  private long adsPort;
  private AmsAddr amsAddr = new AmsAddr();
  public static final int DEFAULT_AMS_PORT = 851;

  /**
  * Class constructor. Called from static method
  */
  private AdsManager() {
    //Initialization list for constructor
    adsPort = 0;
  }

  /**
  * Method for creating exactly 1 instance of the AdsManager class
  * @return The instance of this class
  */
  public synchronized static AdsManager create() {
    if(!alive)
      return objRef = new AdsManager();

    else {
      System.out.println("ADS Manager was already created!");
      return objRef;
    }
  }

  /**
  * Delete instance (abandon) by setting internal reference to null
  * @return Current instance reference (set to null)
  */
  public synchronized static AdsManager delete() {
      //Close port before abandonig the reference
      if(objRef.getPort() != 0)
        objRef.closePort();
      alive = false;

      return objRef = null;
  }

  /**
  * Method for opening ADS port
  * @return ADS port number
  * @param amsPort Specified AMS port number
  * @exception AdsException On fail to retrieve AMS Net ID
  */
  public synchronized long openPort(int amsPort) throws AdsException {
    long errId = 0;

    //Open ADS port first
    if(adsPort == 0)
      adsPort = AdsCallDllFunction.adsPortOpen();
    else
      System.out.println("Port already opened! ADS Port: " + adsPort);

    //Port opened - get AMS address
    errId = AdsCallDllFunction.getLocalAddress(amsAddr);
    if(errId != 0) {
      System.out.println("Failed to open ADS port!");
      AdsCallDllFunction.adsPortClose();
      adsPort = 0;
      throw new AdsException(errId);
    }
    amsAddr.setPort(amsPort);
    System.out.println("ADS port: " + adsPort + "\nAMS Net ID: " + amsAddr.getNetIdString()
                      + "\nAMS port: " + amsAddr.getPort());
    return adsPort;
  }

  /**
  * Method for opening ADS port with default AMS port
  * @return ADS port number
  * @exception AdsException On fail to retrieve AMS Net ID
  */
  public synchronized long openPort() throws AdsException {
    return openPort(DEFAULT_AMS_PORT);
  }

  /**
  * Method for getting current ADS port
  * @return ADS port (0 - if closed)
  */
  public long getPort() {
    return adsPort;
  }

  /**
  * Method for getting AMS net ID in String format
  * @return AMS net ID as a String
  */
  public String getAmsAddr() {
    return amsAddr.getNetIdString();
  }

  /**
  * Method for getting AMS port number
  * @return AMS port number
  */
  public long getAmsPort() {
    return amsAddr.getPort();
  }

  /**
  * Method for closing ADS port
  * @return True if port closed successfully
  * @exception AdsException On fail to close ADS port
  */
  public synchronized boolean closePort() {
    long errId = AdsCallDllFunction.adsPortClose();
    adsPort = 0;
    return (errId == 0);
  }

  /**
  * Method for reading ADS state
  * @return ADS error ID (0 - no error)
  * @param adsStateBuff State of ADS connection
  * @param adsDevStateBuff State of ADS device
  * @exception AdsPortClosedException When ADS port has not been opened
  */
  public synchronized long readState(AdsState adsStateBuff, AdsState adsDevStateBuff)
                              throws AdsPortClosedException {
    long errId = 0;

    if(adsPort != 0)
      errId = AdsCallDllFunction.adsSyncReadStateReq(amsAddr, adsStateBuff, adsDevStateBuff);
    else throw new AdsPortClosedException();

    return errId;
  }

  /**
  * Method for reading ADS device info
  * @return ADS error ID (0 - no error)
  * @param adsStateBuff State of ADS connection
  * @param adsDevStateBuff State of ADS device
  * @exception AdsPortClosedException When ADS port has not been opened
  */
  public synchronized long readDeviceInfo(AdsDevName devName, AdsVersion adsVersion)
                                   throws AdsPortClosedException {
    long errId = 0;

    if (adsPort != 0)
      errId = AdsCallDllFunction.adsSyncReadDeviceInfoReq(amsAddr, devName, adsVersion);
    else throw new AdsPortClosedException();

    return errId;
  }

  /**
  * Method for setting ADS communication timeout
  * @return ADS error ID (0 - no error)
  * @param adsTimeout Timeout setpoint in milliseconds
  * @exception AdsPortClosedException When ADS port has not been opened
  */
  public synchronized long setAdsTimeout(long adsTimeout)
                                  throws AdsPortClosedException {
    long errId = 0;

    if (adsPort != 0)
      errId = AdsCallDllFunction.adsSyncSetTimeout(adsTimeout);
    else throw new AdsPortClosedException();

    return errId;
  }

  /**
  * Method for getting handle to ADS variable. Provides read & write access
  * @return Symbol handle as long
  * @param varName Name of the variable to which the handle is to be obtained
  * @exception AdsPortClosedException When ADS port has not been opened
  * @exception AdsSymbolException On fail to read symbol
  */
  public synchronized long getHandle(String varName)
                              throws AdsPortClosedException, AdsException {
    JNIByteBuffer handlBuff = new JNIByteBuffer(4); //Handler buffer variable
    JNIByteBuffer symBuff = new JNIByteBuffer(varName.getBytes());
    long errId = 0;

    //Get handle to the variable
    if(adsPort != 0) {
      errId = AdsCallDllFunction.adsSyncReadWriteReq(amsAddr, AdsCallDllFunction.ADSIGRP_SYM_HNDBYNAME, 0x0,
                                                    handlBuff.getUsedBytesCount(), handlBuff,
                                                    symBuff.getUsedBytesCount(), symBuff);
      if(errId != 0) throw new AdsException(errId);
    } else throw new AdsPortClosedException();

    return Convert.ByteArrToInt(handlBuff.getByteArray());
  }

  /**
  * Method for releasing handle to ADS variable
  * @param symHandle Handle to ADS variable
  * @return True if successful
  */
  public synchronized boolean releaseHandle(long symHandle) {
    JNIByteBuffer handlBuff = new JNIByteBuffer(Long.BYTES);
    ByteBuffer byteBuff = ByteBuffer.allocate(Long.BYTES);
    byteBuff.putLong(symHandle);
    handlBuff.setByteArray(byteBuff.array(), true);

    long errId = AdsCallDllFunction.adsSyncWriteReq(amsAddr, AdsCallDllFunction.ADSIGRP_SYM_RELEASEHND, 0x0,
                                                   handlBuff.getUsedBytesCount(), handlBuff);
    if(errId == 0) return true;
    else return false;
  }

  /**
  * Method for reading ADS variable by handle
  * @return ADS variable value as byte array
  * @param symHandle Handle to ADS variable
  * @param dataSize Size of ADS variable in bytes
  * @exception AdsPortClosedException When ADS port has not been opened
  * @exception AdsSymbolException On fail to read symbol
  * @see getHandle
  */
  public synchronized byte[] readByHandle(long symHandle, int dataSize)
                                   throws AdsPortClosedException, AdsException {
    JNIByteBuffer dataBuff = new JNIByteBuffer(dataSize);
    long errId = 0;

    if(adsPort != 0) {
      errId = AdsCallDllFunction.adsSyncReadReq(amsAddr, AdsCallDllFunction.ADSIGRP_SYM_VALBYHND, symHandle,
                                                dataSize, dataBuff);
      if(errId != 0) throw new AdsException(errId);
    } else throw new AdsPortClosedException();

    return dataBuff.getByteArray();
  }

  /**
  * Method for reading ADS variable by variable name (symbol)
  * @return ADS variable value as byte array
  * @param varName Variable name as String
  * @param dataSize Size of ADS variable in bytes
  * @exception AdsPortClosedException When ADS port has not been opened
  * @exception AdsSymbolException On fail to read symbol
  * @see getHandle
  */
  public synchronized byte[] readBySymbol(String varName, int dataSize)
                                   throws AdsPortClosedException, AdsException {
    JNIByteBuffer dataBuff = new JNIByteBuffer(dataSize);
    long errId = 0;
    long symHandle = 0;

    symHandle = getHandle(varName); //Get handle to the variable, throws AdsPortClosedException

    //Get variable value by handle (index offset)
    errId = AdsCallDllFunction.adsSyncReadReq(amsAddr, AdsCallDllFunction.ADSIGRP_SYM_VALBYHND, symHandle,
                                                dataSize, dataBuff);
    if(errId != 0) throw new AdsException(errId);

    releaseHandle(symHandle); //Release handle to the variable

    return dataBuff.getByteArray();
  }

  /**
  * Method for writing to ADS variable by handle.
  * @return True if successful
  * @param symHandle Handle to ADS variable
  * @param newVal New value to be written to ADS variable as byte array
  * @exception AdsPortClosedException When ADS port has not been opened
  * @see getHandle
  */
  public synchronized boolean writeByHandle(long symHandle, byte[] newVal)
                                     throws AdsPortClosedException {
    JNIByteBuffer dataBuff = new JNIByteBuffer(newVal);
    long errId = 0;

    //Get variable by handle
    if (adsPort != 0) {
      errId = AdsCallDllFunction.adsSyncWriteReq(amsAddr, AdsCallDllFunction.ADSIGRP_SYM_VALBYHND, symHandle,
                                                dataBuff.getUsedBytesCount(), dataBuff);
    } else throw new AdsPortClosedException();

    return (errId == 0);
  }

  /**
  * Method for writing to ADS variable by variable name (symbol)
  * @return True if successful
  * @param varName Name to ADS variable
  * @param newVal New value to be written to ADS variable as byte array
  * @exception AdsPortClosedException When ADS port has not been opened
  * @see getHandle
  */
  public synchronized boolean writeBySymbol(String varName, byte[] newVal)
                                     throws AdsPortClosedException {
    JNIByteBuffer dataBuff = new JNIByteBuffer(newVal);
    long symHandle;
    long errId = 0;

    symHandle = getHandle(varName); //Get handle to the variable, throws AdsPortClosedException
    errId = AdsCallDllFunction.adsSyncWriteReq(amsAddr, AdsCallDllFunction.ADSIGRP_SYM_VALBYHND, symHandle,
                                              dataBuff.getUsedBytesCount(), dataBuff); //Write variable by handle
    releaseHandle(symHandle); //Release handle to the variable

    return (errId == 0);
  }
}
