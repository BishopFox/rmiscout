package com.bishopfox.example;

import java.rmi.Remote;
import java.rmi.RemoteException;
import java.util.*;

public interface Hello extends Remote {
    String restart() throws RemoteException;
    String sayHello(String name) throws RemoteException;
    String sayNumber(int name) throws RemoteException;
    boolean login(String email, String password) throws RemoteException;

    String sayTest1(int name) throws RemoteException;
    String sayTest2(byte name) throws RemoteException;
    String sayTest3(short name) throws RemoteException;
    String sayTest4(long name) throws RemoteException;
    String sayTest5(char name) throws RemoteException;
    String sayTest6(boolean name) throws RemoteException;
    String sayTest7(float name) throws RemoteException;
    String sayTest8(double name) throws RemoteException;
    String sayTest9(Map name) throws RemoteException;
    String sayTest10(HashMap name) throws RemoteException;
    String sayTest11(List name) throws RemoteException;
    String sayTest12(Object name) throws RemoteException;

    ArrayList<String> say2things(String name, int test) throws RemoteException;
}
