package org.dynamicruntime.util;

import com.maxmind.geoip2.DatabaseReader;
import com.maxmind.geoip2.exception.GeoIp2Exception;
import com.maxmind.geoip2.model.CityResponse;
import org.dynamicruntime.exception.DnException;

import java.io.File;
import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.List;

import static org.dynamicruntime.util.DnCollectionUtil.mList;

@SuppressWarnings("WeakerAccess")
public class IpLocationUtil {
    public static volatile DatabaseReader dbReader;

    public static void init(String dbLocation) throws DnException {
        if (dbReader != null) {
            return;
        }
        try {
            dbReader = new DatabaseReader.Builder(new File(dbLocation)).build();
        }
        catch (IOException e) {
            throw new DnException("Could open GEO database at " + dbLocation + ".", e);
        }
    }

    /**
     * Attempts to lookup IP address and resolve it an array of strings
     * in the order [%Country%, %State%, %City%, %Postal Code%]
     */
    public static List<String> getLocation(String ipAddress) throws DnException {
        if (dbReader == null) {
            return null;
        }
        InetAddress inetAddress;
        try {
            inetAddress = InetAddress.getByName(ipAddress);
        } catch (UnknownHostException e) {
            throw new DnException("Unable to resolve ip Address " + ipAddress + ".", e);
        }

        try {
            CityResponse cr = dbReader.city(inetAddress);

            String countryName = cr.getCountry().getName();
            String state = cr.getLeastSpecificSubdivision().getName();
            String cityName = cr.getCity().getName();
            String postal = cr.getPostal().getCode();
            return mList(countryName, state, cityName, postal);
        } catch (GeoIp2Exception ge) {
            return null;
        } catch (IOException e) {
            throw new DnException("Error getting City for ip address " + ipAddress + ".", e);
        }
    }
}
