package me.shkschneider.openlocationcodes;

// Copyright 2014 Google Inc. All rights reserved.
//
// Licensed under the Apache License, Version 2.0 (the 'License');
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an 'AS IS' BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

import android.location.Location;
import android.support.annotation.NonNull;

import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.LatLngBounds;

import java.util.Locale;

// Open Location Codes were developed at Google's Zurich engineering office, and then open sourced so that they can be freely used.
// The main author is Doug Rinckes (@drinckes), with the help of lots of colleagues including:
// Philipp Bunge, Aner Ben-Artzi, Jarda Bengl, Prasenit Phukan, Sacha van Ginhoven and Zongwei Li
//
// http://openlocationcode.com
//
// Java port by ShkSchneider <https://shkschneider.me>
// From <https://github.com/google/open-location-code>
public class OpenLocationCodes {

    public static final int CODE_DEFAULT_LENGTH = 11;

    private static final String SEPARATOR = "+";
    private static final int SEPARATOR_POSITION = 8;
    private static final String PADDING_CHARACTER = "0";
    private static final String CODE_ALPHABET = "23456789CFGHJMPQRVWX";
    private static final int ENCODING_BASE = CODE_ALPHABET.length();
    private static final int LATITUDE_MAX = 90;
    private static final int LONGITUDE_MAX = 180;
    private static final int PAIR_CODE_LENGTH = 10;
    private static final float[] PAIR_RESOLUTIONS = {
            20.0F, 1.0F, 0.05F, 0.0025F, 0.000125F
    };
    private static final int CODE_MIN_LENGTH = SEPARATOR_POSITION;
    private static final int CODE_MAX_LENGTH = 11;
    private static final int GRID_COLUMNS = 4;
    private static final int GRID_ROWS = 5;
    private static final float GRID_SIZE_DEGREES = 0.000125F;

    // Checks

    private static boolean isValid(String code) {
        if (code == null || code.length() < 2) {
            return false;
        }
        // There must be exactly one separator.
        final int position = code.indexOf(SEPARATOR);
        if (position == -1) {
            return false;
        }
        if (position != code.lastIndexOf(SEPARATOR)) {
            return false;
        }
        if ((position % 2) != 0) {
            return false;
        }
        // Check characters before separator: padding or alphabet
        final String beforeSeparator = code.substring(0, position);
        for (final char c : beforeSeparator.toCharArray()) {
            final String s = String.valueOf(c);
            if (! s.equals(PADDING_CHARACTER) && ! CODE_ALPHABET.contains(s)) {
                return false;
            }
        }
        // Check characters after separator: alphabet only
        final String afterSeparator = code.substring(position + 1, code.length());
        for (final char c : afterSeparator.toCharArray()) {
            final String s = String.valueOf(c);
            if (! CODE_ALPHABET.contains(s)) {
                return false;
            }
        }
        return true;
    }

    private static boolean isShort(String code) {
        // Check it's valid.
        if (! isValid(code)) {
            return false;
        }
        // If there are less characters than expected before the SEPARATOR.
        if (code.contains(SEPARATOR) && code.indexOf(SEPARATOR) < SEPARATOR_POSITION) {
            return true;
        }
        return false;
    }

    private static boolean isFull(String code) {
        if (! isValid(code)) {
            return false;
        }
        // If it's short, it's not full.
        if (isShort(code)) {
            return false;
        }
        // Work out what the first latitude character indicates for latitude.
        final int firstLatValue = CODE_ALPHABET.indexOf(code.charAt(0)) * ENCODING_BASE;
        if (firstLatValue >= LATITUDE_MAX * 2) {
            // The code would decode to a latitude of >= 90 degrees.
            return false;
        }
        if (code.length() > 1) {
            // Work out what the first longitude character indicates for longitude.
            final int firstLngValue = CODE_ALPHABET.indexOf(code.charAt(0)) * ENCODING_BASE;
            if (firstLngValue >= LONGITUDE_MAX * 2) {
                // The code would decode to a longitude of >= 180 degrees.
                return false;
            }
        }
        return true;
    }

    public static boolean isPadded(final String code) {
        return code.contains(PADDING_CHARACTER);
    }

    public static boolean contains(final String code, final double latitude, final double longitude) {
        if (! isValid(code)) {
            return false;
        }
        final CodeArea codeArea = decode(code);
        return (codeArea.bounds().contains(new LatLng(latitude, longitude)));
    }

    // Encode

    public static String encode(final double latitude, final double longitude) {
        return encode(latitude, longitude, CODE_MAX_LENGTH);
    }

    public static String encode(double latitude, double longitude, final int codeLength) throws IllegalArgumentException {
        if (codeLength < 4 || (codeLength < 10 && (codeLength % 2) == 1)) {
            throw new IllegalArgumentException("Invalid Open Location Code length");
        }
        // Ensure that latitude and longitude are valid.
        latitude = clipLatitude(latitude);
        longitude = normalizeLongitude(longitude);
        // Latitude 90 needs to be adjusted to be just less, so the returned code can also be decoded.
        if (latitude == 90) {
            latitude = latitude - computeLatitudePrecision(codeLength);
        }
        String code = encodePairs(latitude, longitude, Math.min(codeLength, PAIR_CODE_LENGTH));
        // If the requested length indicates we want grid refined codes.
        if (codeLength > PAIR_CODE_LENGTH) {
            code += encodeGrid(latitude, longitude, codeLength - PAIR_CODE_LENGTH);
        }
        // Tricky last fail-safe filling
        while (code.length() < CODE_MIN_LENGTH) {
            code += PADDING_CHARACTER;
        }
        if (code.length() == SEPARATOR_POSITION) {
            code += SEPARATOR;
        }
        return code;
    }

    private static double clipLatitude(final double latitude) {
        return Math.min(90, Math.max(-90, latitude));
    }

    private static double computeLatitudePrecision(final int codeLength) {
        if (codeLength <= 10) {
            return Math.pow(20, Math.floor(codeLength / -2 + 2));
        }
        return Math.pow(20, -3) / Math.pow(GRID_ROWS, codeLength - 10);
    }

    private static double normalizeLongitude(double longitude) {
        while (longitude < -180) {
            longitude = longitude + 360;
        }
        while (longitude >= 180) {
            longitude = longitude - 360;
        }
        return longitude;
    }

    private static String encodePairs(final double latitude, final double longitude, final int codeLength) {
        String code = "";
        // Adjust latitude and longitude so they fall into positive ranges.
        double adjustedLatitude = latitude + LATITUDE_MAX;
        double adjustedLongitude = longitude + LONGITUDE_MAX;
        // Count digits - can't use string length because it may include a separator character.
        int digitCount = 0;
        while (digitCount < codeLength) {
            // Provides the value of digits in this place in decimal degrees.
            float placeValue = PAIR_RESOLUTIONS[(int) Math.floor(digitCount / 2)];
            // Do the latitude - gets the digit for this place and subtracts that for the next digit.
            int digitValue = (int) Math.floor(adjustedLatitude / placeValue);
            adjustedLatitude -= digitValue * placeValue;
            code += CODE_ALPHABET.charAt(digitValue);
            digitCount += 1;
            if (digitCount == codeLength) {
                break ;
            }
            // And do the longitude - gets the digit for this place and subtracts that for the next digit.
            digitValue = (int) Math.floor(adjustedLongitude / placeValue);
            adjustedLongitude -= digitValue * placeValue;
            code += CODE_ALPHABET.charAt(digitValue);
            digitCount += 1;
            // Should we add a separator here?
            if (digitCount == SEPARATOR_POSITION && digitCount < codeLength) {
                code += SEPARATOR;
            }
        }
        if (code.length() < SEPARATOR_POSITION) {
            for (int x = 0 ; x < SEPARATOR_POSITION - code.length() + 1; x++) {
                code += PADDING_CHARACTER;
            }
        }
        if (code.length() == SEPARATOR_POSITION) {
            code += SEPARATOR;
        }
        return code;
    }

    private static String encodeGrid(final double latitude, final double longitude, final int codeLength) {
        String code = "";
        float latPlaceValue = GRID_SIZE_DEGREES;
        float lngPlaceValue = GRID_SIZE_DEGREES;
        // Adjust latitude and longitude so they fall into positive ranges and get the offset for the required places.
        double adjustedLatitude = (latitude + LATITUDE_MAX) % latPlaceValue;
        double adjustedLongitude = (longitude + LONGITUDE_MAX) % lngPlaceValue;
        for (int i = 0; i < codeLength; i++) {
            // Work out the row and column.
            int row = (int) Math.floor(adjustedLatitude / (latPlaceValue / GRID_ROWS));
            int col = (int) Math.floor(adjustedLongitude / (lngPlaceValue / GRID_COLUMNS));
            latPlaceValue /= GRID_ROWS;
            lngPlaceValue /= GRID_COLUMNS;
            adjustedLatitude -= row * latPlaceValue;
            adjustedLongitude -= col * lngPlaceValue;
            code += CODE_ALPHABET.charAt(row * GRID_COLUMNS + col);
        }
        return code;
    }

    // Decode

    public static CodeArea decode(String code) throws IllegalArgumentException {
        if (! isFull(code)) {
            throw new IllegalArgumentException("Passed Open Location Code is not a valid full code: " + code);
        }
        // Strip out separator character (we've already established the code is valid so the maximum is one),
        // padding characters and convert to upper case.
        code = code.replace(SEPARATOR, "");
        code = code.replaceAll("[0+]", "");
        code = code.toUpperCase(Locale.US);
        // Decode the lat/lng pair component.
        final CodeArea codeArea = decodePairs(((code.length() <= PAIR_CODE_LENGTH) ? code : code.substring(0, PAIR_CODE_LENGTH)));
        // If there is a grid refinement component, decode that.
        if (code.length() <= PAIR_CODE_LENGTH) {
            return codeArea;
        }
        final CodeArea gridArea = decodeGrid(code.substring(PAIR_CODE_LENGTH));
        return new CodeArea(codeArea.latitudeLo + gridArea.latitudeLo,
                codeArea.longitudeLo + gridArea.longitudeLo,
                codeArea.latitudeLo + gridArea.latitudeHi,
                codeArea.longitudeLo + gridArea.longitudeHi,
                codeArea.codeLength + gridArea.codeLength);
    }

    private static CodeArea decodePairs(final String code) {
        // Get the latitude and longitude values. These will need correcting from positive ranges.
        final double[] latitude = decodePairsSequence(code, 0);
        final double[] longitude = decodePairsSequence(code, 1);
        // Correct the values and set them into the CodeArea object.
        return new CodeArea(latitude[0] - LATITUDE_MAX,
                longitude[0] - LONGITUDE_MAX,
                latitude[1] - LATITUDE_MAX,
                longitude[1] - LONGITUDE_MAX,
                code.length());
    }

    private static double[] decodePairsSequence(final String code, final int offset) {
        int i = 0;
        double value = 0;
        while (i * 2 + offset < code.length()) {
            value += CODE_ALPHABET.indexOf(code.charAt(i * 2 + offset)) * PAIR_RESOLUTIONS[i];
            i += 1;
        }
        return new double[] { value, value + PAIR_RESOLUTIONS[i - 1] };
    }

    private static CodeArea decodeGrid(final String code) {
        double latitudeLo = 0.0D;
        double longitudeLo = 0.0D;
        float latPlaceValue = GRID_SIZE_DEGREES;
        float lngPlaceValue = GRID_SIZE_DEGREES;
        int i = 0;
        while (i < code.length()) {
            int codeIndex = CODE_ALPHABET.indexOf(code.charAt(i));
            double row = Math.floor(codeIndex / GRID_COLUMNS);
            double col = codeIndex % GRID_COLUMNS;
            latPlaceValue /= GRID_ROWS;
            lngPlaceValue /= GRID_COLUMNS;
            latitudeLo += row * latPlaceValue;
            longitudeLo += col * lngPlaceValue;
            i += 1;
        }
        return new CodeArea(latitudeLo, longitudeLo, latitudeLo + latPlaceValue, longitudeLo + lngPlaceValue, code.length());
    }

    // Shorten

    public static String shorten(String code, double latitude, double longitude) throws IllegalArgumentException {
        if (! isFull(code)) {
            throw new IllegalArgumentException("Passed code is not valid and full: " + code);
        }
        if (code.contains(PADDING_CHARACTER)) {
            throw new IllegalArgumentException("Cannot shorten padded codes: " + code);
        }
        final CodeArea codeArea = decode(code);
        final double latitudeDiff = Math.abs(latitude - codeArea.latitudeCenter);
        final double longitudeDiff = Math.abs(longitude - codeArea.longitudeCenter);
        if (latitudeDiff < (computeLatitudePrecision(8) / 4) && longitudeDiff < (computeLatitudePrecision(8) / 4)) {
            return encode(latitude, longitude).substring(6);
        }
        if (latitudeDiff < (computeLatitudePrecision(6) / 4) && longitudeDiff < (computeLatitudePrecision(6) / 4)) {
            return encode(latitude, longitude).substring(6);
        }
        if (latitudeDiff < (computeLatitudePrecision(4) / 4) && longitudeDiff < (computeLatitudePrecision(4) / 4)) {
            return encode(latitude, longitude).substring(4);
        }
        throw new IllegalArgumentException("Reference location is too far from the Open Location Code center.");
    }

    // Recover (from shorten)

    public static String recover(String shortCode, double referenceLatitude, double referenceLongitude) {
        return recover(shortCode, referenceLatitude, referenceLongitude, CODE_MAX_LENGTH);
    }

    public static String recover(String shortCode, double referenceLatitude, double referenceLongitude, final int codeLength) {
        if (! isShort(shortCode)) {
            if (isFull(shortCode)) {
                return shortCode;
            }
            else {
                throw new IllegalArgumentException("Passed short code is not valid: " + shortCode);
            }
        }
        referenceLatitude = clipLatitude(referenceLatitude);
        referenceLongitude = normalizeLongitude(referenceLongitude);
        final int digitsToRecover = SEPARATOR_POSITION - shortCode.indexOf(SEPARATOR);
        // The precision (height and width) of the missing prefix in degrees.
        final double prefixPrecision = Math.pow(CODING_BASE.intValue(), 2 - (digitsToRecover / 2));
        // Use the reference location to pad the supplied short code and decode it.
        final String recovered = encode(referenceLatitude, referenceLongitude).substring(0, digitsToRecover) + shortCode;
        final CodeArea codeArea = decode(recovered);
        double recoveredLatitude = codeArea.latitudeCenter;
        double recoveredLongitude = codeArea.longitudeCenter;
        // Move the recovered latitude by one precision up or down if it is too far from the reference,
        // unless doing so would lead to an invalid latitude.
        double latitudeDiff = recoveredLatitude - referenceLatitude;
        if (latitudeDiff > prefixPrecision / 2 && recoveredLatitude - prefixPrecision > -LATITUDE_MAX) {
            recoveredLatitude -= prefixPrecision;
        }
        else if (latitudeDiff < -prefixPrecision / 2 && recoveredLatitude + prefixPrecision < LATITUDE_MAX) {
            recoveredLatitude += prefixPrecision;
        }
        // Move the recovered longitude by one resolution up or down if it is too far from the reference.
        double longitudeDiff = codeArea.longitudeCenter - referenceLongitude;
        if (longitudeDiff > prefixPrecision / 2) {
            recoveredLongitude -= prefixPrecision;
        } else if (longitudeDiff < -prefixPrecision / 2) {
            recoveredLongitude += prefixPrecision;
        }

        return encode(recoveredLatitude, recoveredLongitude, codeLength);
    }

    // Distance

    public static float distance(@NonNull final CodeArea codeArea) {
        float[] results = new float[1];
        Location.distanceBetween(codeArea.northwest().latitude, codeArea.northwest().longitude, codeArea.northeast().latitude, codeArea.northeast().longitude, results);
        final float distance1 = results[0];
        Location.distanceBetween(codeArea.northwest().latitude, codeArea.northwest().longitude, codeArea.southeast().latitude, codeArea.southeast().longitude, results);
        final float distance2 = results[0];
        return (distance1 + distance2) / 2;
    }

    // CodeArea

    public static class CodeArea {

        public double latitudeLo;
        public double longitudeLo;
        public double latitudeHi;
        public double longitudeHi;
        public int codeLength;
        public double latitudeCenter;
        public double longitudeCenter;

        CodeArea(final double latitudeLo, final double longitudeLo, final double latitudeHi, final double longitudeHi, final int codeLength) {
            this.latitudeLo = latitudeLo;
            this.longitudeLo = longitudeLo;
            this.latitudeHi = latitudeHi;
            this.longitudeHi = longitudeHi;
            this.codeLength = codeLength;
            this.latitudeCenter = Math.min(latitudeLo + (latitudeHi - latitudeLo) / 2, LATITUDE_MAX);
            this.longitudeCenter = Math.min(longitudeLo + (longitudeHi - longitudeLo) / 2, LONGITUDE_MAX);
        }

        public LatLng center() {
            return new LatLng(this.latitudeCenter, this.longitudeCenter);
        }

        public LatLng northwest() {
            return new LatLng(this.latitudeHi, this.longitudeLo);
        }

        public LatLng northeast() {
            return new LatLng(this.latitudeHi, this.longitudeHi);
        }

        public LatLng southwest() {
            return new LatLng(this.latitudeLo, this.longitudeLo);
        }

        public LatLng southeast() {
            return new LatLng(this.latitudeLo, this.longitudeHi);
        }

        public LatLngBounds bounds() {
            return new LatLngBounds(southwest(), northeast());
        }

        @Override
        public String toString() {
            return bounds().toString();
        }

    }

}
