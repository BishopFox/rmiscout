package com.bishopfox.rmiscout;

import org.omg.CORBA.portable.ApplicationException;
import org.omg.CORBA.portable.RemarshalException;
import org.omg.CORBA_2_3.portable.OutputStream;

import javax.rmi.CORBA.Stub;
import java.util.List;

public class IIOPStub extends Stub
{
    private static final String[] _type_ids = { "RMI:DummyInterface:0000000000000000" };

    public String[] _ids()
    {
        // Unused method. Requires override for Stub superclass
        return _type_ids.clone();
    }

    public boolean execute(String operation, List<ParamPair> params, String signature, boolean responseExpected) {
        org.omg.CORBA_2_3.portable.InputStream localInputStream = null;
        org.omg.CORBA_2_3.portable.InputStream response = null;
        boolean success = false;

        try
        {
            OutputStream localOutputStream = (OutputStream)_request(operation, true);
            for (ParamPair p : params) {
                //localOutputStream.write_value(p.value, p.name);
                switch(p.value.getClass().getName()) {
                    case "java.lang.Integer":
                        localOutputStream.write_long((int)p.value);
                        break;
                    case "[I":
                        localOutputStream.write_long_array((int[])p.value,0, ((int[])p.value).length);
                        break;
                    case "java.lang.Long":
                        localOutputStream.write_longlong((int)p.value);
                        break;
                    case "[J":
                        localOutputStream.write_longlong_array((long[])p.value,0, ((long[])p.value).length);
                        break;
                    case "java.lang.Short":
                        localOutputStream.write_short((short)p.value);
                        break;
                    case "[S":
                        localOutputStream.write_short_array((short[])p.value,0, ((short[])p.value).length);
                        break;
                    case "java.lang.Boolean":
                        localOutputStream.write_boolean((boolean)p.value);
                        break;
                    case "[Z":
                        localOutputStream.write_boolean_array((boolean[])p.value,0, ((boolean[])p.value).length);
                        break;
                    case "java.lang.Float":
                        localOutputStream.write_float((float)p.value);
                        break;
                    case "[F":
                        localOutputStream.write_float_array((float[])p.value,0, ((float[])p.value).length);
                        break;
                    case "java.lang.Double":
                        localOutputStream.write_double((double)p.value);
                        break;
                    case "[D":
                        localOutputStream.write_double_array((double[])p.value,0, ((double[])p.value).length);
                        break;
                    case "java.lang.Char":
                        localOutputStream.write_char((char)p.value);
                        break;
                    case "[C":
                        localOutputStream.write_char_array((char[])p.value,0, ((char[])p.value).length);
                        break;
                    case "java.lang.Byte":
                        localOutputStream.write_octet((byte)p.value);
                        break;
                    case "[B":
                        localOutputStream.write_octet_array((byte[])p.value,0, ((byte[])p.value).length);
                        break;
                    default:
                        localOutputStream.write_value(p.value, p.name);
                        break;
                }
            }

            Utilities.toggleSystemErr();
            response = (org.omg.CORBA_2_3.portable.InputStream)_invoke(localOutputStream);
            Utilities.toggleSystemErr();

            // Statement only reached if operation exists
            success = true;
            System.out.println(Utilities.Colors.YELLOW + "Executed: " + signature + Utilities.Colors.ENDC);
            if (responseExpected) {
                String retVal = "unknown";
                String returnType = signature.substring(0, signature.indexOf(" "));

                switch (returnType) {
                    case "int":
                        retVal = String.valueOf(response.read_long());
                        break;
                    case "int[]":
                        retVal = String.valueOf(response.read_value(new int[0].getClass()));
                        break;
                    case "long":
                        retVal = String.valueOf(response.read_longlong());
                        break;
                    case "long[]":
                        retVal = String.valueOf(response.read_value(new long[0].getClass()));
                        break;
                    case "short":
                        retVal = String.valueOf(response.read_short());
                        break;
                    case "short[]":
                        retVal = String.valueOf(response.read_value(new short[0].getClass()));
                        break;
                    case "boolean":
                        retVal = String.valueOf(response.read_boolean());
                        break;
                    case "boolean[]":
                        retVal = String.valueOf(response.read_value(new boolean[0].getClass()));
                        break;
                    case "float":
                        retVal = String.valueOf(response.read_float());
                        break;
                    case "float[]":
                        retVal = String.valueOf(response.read_value(new float[0].getClass()));
                        break;
                    case "double":
                        retVal = String.valueOf(response.read_double());
                        break;
                    case "double[]":
                        retVal = String.valueOf(response.read_value(new double[0].getClass()));
                        break;
                    case "char":
                        retVal = String.valueOf(response.read_char());
                        break;
                    case "char[]":
                        retVal = String.valueOf(response.read_value(new char[0].getClass()));
                        break;
                    case "byte":
                        retVal = String.valueOf(response.read_octet());
                        break;
                    case "byte[]":
                        retVal = String.valueOf(response.read_value(new byte[0].getClass()));
                        break;
                    case "String":
                        retVal = (String) response.read_value(String.class);
                        break;
                    case "String[]":
                        retVal = (String) response.read_value(new String[0].getClass());
                        break;
                }

                System.out.println("\tResponse [" + returnType + "] = " + retVal);
            }
        }
        catch (ApplicationException localApplicationException)
        {
            localInputStream = (org.omg.CORBA_2_3.portable.InputStream)localApplicationException.getInputStream();
            String resp = localInputStream.read_string();
            localApplicationException.printStackTrace();
        }
        catch (RemarshalException localRemarshalException)
        {
            localRemarshalException.printStackTrace();
        }
        catch (org.omg.CORBA.BAD_OPERATION e) {
            // Operation does not exist
//            e.printStackTrace();
        }
        catch (org.omg.CORBA.portable.UnknownException e) {
            if (e.originalEx instanceof ClassCastException) {
                //exists; Type mismatch
                System.out.println(Utilities.Colors.GREEN + "Found: " + signature + Utilities.Colors.ENDC);
                success = true;
            }
            else if (e.originalEx instanceof IllegalArgumentException) {
                //exists; too few arguments
                System.out.println(Utilities.Colors.GREEN + "Found: " + signature + Utilities.Colors.ENDC);
                success = true;
            }

            else {
                // Unknown condition
                e.printStackTrace();

            }
        }
        catch(org.omg.CORBA.MARSHAL e) {
            if (e.getMessage().contains("ClassNotFoundException")) {
                System.out.println(Utilities.Colors.GREEN + "Found: " + signature + Utilities.Colors.ENDC);
            }
            else {
                // Unknown condition
                e.printStackTrace();
                if (response != null) {
                    System.err.println(Utilities.Colors.RED + "[ERROR] Failed to unmarshal return value (wrong type?). " +
                            "Consider using the \"void\" return type to ignore the return value." + Utilities.Colors.ENDC);
                }
            }
        }
        catch(org.omg.CORBA.BAD_PARAM e) {
            System.out.println(Utilities.Colors.GREEN + "Found (Potential type mismatch): " + signature + Utilities.Colors.ENDC);
            success = true;
        }
        catch (Exception e) {
            e.printStackTrace();
        }
        finally
        {
            _releaseReply(localInputStream);
        }
        return success;
    }
}
