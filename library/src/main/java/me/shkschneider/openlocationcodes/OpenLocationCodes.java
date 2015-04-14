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

import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.LatLngBounds;

// Open Location Codes were developed at Google's Zurich engineering office, and then open sourced so that they can be freely used.
// The main author is Doug Rinckes (@drinckes), with the help of lots of colleagues including:
// Philipp Bunge, Aner Ben-Artzi, Jarda Bengl, Prasenit Phukan, Sacha van Ginhoven
//
// http://openlocationcode.com
//
// Java port by ShkSchneider <https://shkschneider.me>
// From <https://github.com/google/open-location-code>
public class OpenLocationCodes {

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
    private static final int GRID_COLUMNS = 4;
    private static final int GRID_ROWS = 5;
    private static final float GRID_SIZE_DEGREES = 0.000125F;
    private static final int MIN_TRIMMABLE_CODE_LEN = 6;

    // Checks

    private static boolean isValid(String code) {
        if (code == null || code.length() == 0) {
            return false;
        }
        // The separator is required.
        if (! code.contains(SEPARATOR)) {
            return false;
        }
        if (code.indexOf(SEPARATOR) != code.lastIndexOf(SEPARATOR)) {
            return false;
        }
        // Is it in an illegal position?
        if (code.indexOf(SEPARATOR) > SEPARATOR_POSITION || code.indexOf(SEPARATOR) % 2 == 1) {
            return false;
        }
        // We can have an even number of padding characters before the separator, but then it must be the final character.
        if (code.contains(PADDING_CHARACTER)) {
            // Not allowed to start with them!
            if (code.indexOf(PADDING_CHARACTER) == 0) {
                return false;
            }
            // There can only be one group and it must have even length.
            // TODO
            // If the code is long enough to end with a separator, make sure it does.
            if (code.charAt(code.length() - 1) != SEPARATOR.charAt(0)) {
                return false;
            }
        }
        // If there are characters after the separator, make sure there isn't just one of them (not legal).
        if (code.length() - code.indexOf(SEPARATOR) - 1 == 1) {
            return false;
        }
        // Strip the separator and any padding characters.
        code = code.replaceAll("\\" + SEPARATOR + "+", "");
        code = code.replaceAll(PADDING_CHARACTER + "+", "");
        // Check the code contains only valid characters.
        int len = code.length();
        for (int i = 0; i < len; i++) {
            char character = code.charAt(i);
            if (character != SEPARATOR.charAt(0) && ! CODE_ALPHABET.contains("" + character)) {
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
                // The code would deode to a longitude of >= 180 degrees.
                return false;
            }
        }
        return true;
    }

    // Encode

    public static String encode(final double latitude, final double longitude) {
        return encode(latitude, longitude, PAIR_CODE_LENGTH);
    }

    public static String encode(double latitude, double longitude, final int codeLength) throws IllegalArgumentException {
        if (codeLength < 2) {
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
        // Decode the lat/lng pair component.
        final CodeArea codeArea = decodePairs(code.substring(0, PAIR_CODE_LENGTH));
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

    public static String shortenBy4(String code, double latitude, double longitude) {
        return shortenBy(4, code, latitude, longitude, 0.25F);
    }

    public static String shortenBy6(String code, double latitude, double longitude) {
        return shortenBy(6, code, latitude, longitude, 0.0125F);
    }

    private static String shortenBy(int trimLength, String code, double latitude, double longitude, float range) throws IllegalArgumentException {
        if (! isFull(code)) {
            throw new IllegalArgumentException("Passed code is not valid and full: " + code);
        }
        final CodeArea codeArea = decode(code);
        if (codeArea.codeLength < MIN_TRIMMABLE_CODE_LEN) {
            throw new IllegalArgumentException("Code length must be at least " + MIN_TRIMMABLE_CODE_LEN);
        }
        // Ensure that latitude and longitude are valid.
        latitude = clipLatitude(latitude);
        longitude = normalizeLongitude(longitude);
        // Are the latitude and longitude close enough?
        if (Math.abs(codeArea.latitudeCenter - latitude) > range || Math.abs(codeArea.longitudeCenter - longitude) > range) {
            // No they're not, so return the original code.
            return code;
        }
        // They are, so we can trim the required number of characters from the code.
        // But first we strip the prefix and separator and convert to upper case.
        String newCode = code.replace(SEPARATOR, "").toUpperCase();
        // And trim the caracters, adding one to avoid the prefix.
        return newCode.substring(trimLength);
    }

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
