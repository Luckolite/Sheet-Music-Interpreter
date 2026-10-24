// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;

/** Conditional original-raster digit likelihood; never curve deletion or semantic admission. */
final class JoinedDigitTemplates {
    private record Numeral(int digit, float aspect, byte[] bits) {
        Numeral(int digit, float aspect, String bits) {
            this(digit, aspect, Base64.getDecoder().decode(bits));
        }
    }

    // SIL-OFL Bravura/Noto24pt plus all four Noto styles at20/28pt; 16x24 at175.
    private static final Numeral[] NUMERALS = {
        new Numeral(
                0, 0.9166667f, "4A/gD/g++D48eDx4PHg8eDz4PPg/+D/4P/g/+Dz4PPg8eDx4/Hj8ePg++D7gD+AP"),
        new Numeral(
                1, 0.5833333f, "gP+A/+D/4P/g/+D/+P/4/5j/mP+f/5//gP+A/4D/gP+A/4D/gP+A/4D/gP/g/+D/"),
        new Numeral(
                2, 0.8333333f, "8B/wHxz8HPx/8H/wf/x//Hz8fPwAfwB/gB+AH+AA4ADwj/CP/P/8//x//H8MHwwf"),
        new Numeral(
                3, 0.8333333f, "8A/wDxx8HHx8fHx8fHx8fBB/EH/gD+APAH8Af3z8fPx//H/8f/x//Hx/fH/wD/AP"),
        new Numeral(
                4, 0.9166667f, "wA/AD+AP4A/gB+AH4DngOeA+4D74P/g/OD84Pxw/HD//////AD8APwA/AD/Af8B/"),
        new Numeral(5, 0.75f, "/H/8f/x//H/8H/wfDAAMAPwf/B8Mfgx+MP4w/vz4/Pj/+P/4//7//jx+PH7wH/Af"),
        new Numeral(
                6, 0.8333333f, "4B/gH/B88Hx8fHx8fHx8fHwAfAD/H/8f/3z/fH/8f/x8/Hz8fPx8/PB88HzgH+Af"),
        new Numeral(
                7, 0.8333333f, "8IPwg//v/+//////j/+P/wN8A3wAcABwAHwAfAAfAB+AH4Af4B/gH+Af4B/gD+AP"),
        new Numeral(
                8, 0.8333333f, "8B/wHxxwHHAc4Bzgf2B/YPx//H/wf/B/8P/w/xz/HP8P8A/wD/AP8BxwHHDwH/Af"),
        new Numeral(
                9, 0.8333333f, "8A/wD3x8fHx8fHx8f/x//Hz8fPz8//z/8P/w/wD8APz8fPx8/Hz8fHwffB/gA+AD"),
        new Numeral(
                0, 0.6666667f, "gACAAPAPeDw8ODw4DHgPeA94D3gPcA9wD/AP8A9wD3APeA94D3gMeDw4PDh4PPAP"),
        new Numeral(
                1, 0.5882353f, "gAOAA/AD/wP/A48DgAOAA4ADgAOAA4ADgAOAA4ADgAOAA4ADgAOAA4APgA/gD///"),
        new Numeral(
                2, 0.6111111f, "AAEAAfg/PHgceBx4HHAAcAB4AHgAeAA4AA4ADgAHwAfAAcAB4AA4gBzAHMD/////"),
        new Numeral(
                3, 0.6111111f, "AAEAAfg/PHgccBxwHPAAcAB4AHgAOMAP4D/gPwB4APAA8ADwAPAE8AfwB/A/fvw/"),
        new Numeral(
                4, 0.7647059f, "AB4AHgAeAB8AH4AdgByAHOAccBxwHHAcGBwMHAwcDBz//wA+AD4AHAAcABwAPoD/"),
        new Numeral(
                5, 0.64705884f, "+H/4f/x/HEAcQBxAHAAcABwAHAD8P/w/BH4AeAB4APAA8ADwAPAE8Ad4B3g/PvwP"),
        new Numeral(
                6, 0.6111111f, "AA4ADsB/+PA48DjwPEA8ABwAHAAfBv9/P/g/+B/wH/AcwBzAHMA88DzwPPD4eOA/"),
        new Numeral(
                7, 0.64705884f, "////////B/AH8AdwAHAAcAB4ADgAOAA4AD4ADgAOAA8ADwAHAAfAB8ABwAHAAeAB"),
        new Numeral(
                8, 0.6111111f, "AAEAAfg/PHgc8BzwHPAc8DxwPHD8ePgP4D/gPzh+HPAfwB/AH8AfwB/wH/A8ePg/"),
        new Numeral(
                9, 0.6111111f, "AAEAAfgPPDgceBx4H/Af8B/wH/Af8B/wPPg8+Pj/wPEA8ADwAHAAeAR4BHgcPvwP"),
        new Numeral(
                0, 0.6666667f, "8A/wD/g/fHw8eDx4P/g/+D/wP/A/8D/wP/A/8D/wP/A/+D/4P/g8eHx8fHz4P/AP"),
        new Numeral(
                1, 0.6111111f, "AA8AD8AP+A//D/8PPA8ADwAPAA8ADwAPAA8ADwAPAA8ADwAPAA8ADwAPAA/8//z/"),
        new Numeral(
                2, 0.6666667f, "+A/4D/w/PHw8eDx4PHgIeAB8AHwAPAA/AA8AD4AHwAPwAPAAeMA88Pz//P//////"),
        new Numeral(
                3, 0.6666667f, "+A/4D/w/PHw8eDx4PHgAeAB8AHyAP/AP8H/wfwB8APgA+AD4DPgP+D98P3z8P/gP"),
        new Numeral(
                4, 0.7777778f, "ABwAHAAfgB+AH4AfwB/gHGAcYBxwHDgcGBwYHBwc////////AB8AHAAfAB/Af8B/"),
        new Numeral(
                5, 0.6666667f, "+H/4f/x//H88cDxwPHAMAAwADAD8D/w/DH8MfwB4AHgA+AD4AHgPeD98P3z/P/wP"),
        new Numeral(
                6, 0.6666667f, "wD/AP/B/+Ph8cHxwPDA8ADwAPAD/P/9/f/h/+D/4P/A88DzwPPh8+Hh4eHj4f/A/"),
        new Numeral(
                7, 0.6666667f, "//////////8PeA94B3gAeAA8ADwAPAA/AA8ADwAPgA+AB4AHgAfAA8ADwAPwA/AA"),
        new Numeral(
                8, 0.6666667f, "8A/wD/g/fHg8eDx4P3g8eHx4fHj4P/AP+D/4P3x/P/g/8D/wD/A/8D/4P/j8f/g/"),
        new Numeral(
                9, 0.6666667f, "8A/wD/g/fHw8eDx4P3g/+D/4P/g/+D/4/P/8//j/wPgAeAB4AHgMfAw/DD/8D/gH"),
        new Numeral(
                0, 0.8888889f, "wD/AP8A/8Pzw/PD8PPw8/D/8P/w//D/8P/w//A88DzwPPw8/Dz8PDw8PDw/8A/wD"),
        new Numeral(
                1, 0.6666667f, "APgA+AD4AP8A/wD/wP/A/zg/OD84PwA/AD8AP8A/wD/AB8AHwAfAB8AHwAf/P/8/"),
        new Numeral(
                2, 0.8888889f, "wD/AP8A/8PDw8PDw8PDw8PD88Pzw/AA/AD8AP/AD8AM8MDwwPDD/P/8//z/zD/MP"),
        new Numeral(
                3, 0.7777778f, "gP+A/4D/4PPg8+Dz4PPg8wD8APwA/IA/gD+APwA8ADwf/B/8H/wfPB88Hzz4D/gP"),
        new Numeral(
                4, 0.8888889f, "AD8APwA/AA8ADwAPwP/A//A/8D/wPzA8MDwwPAw/DD//P/8//z8ADwAPAA/wP/A/"),
        new Numeral(
                5, 0.8888889f, "8P/w//D/8D/wP/A/MAAwAPwP/A/8Dzw/PD88PwA/AD8PPw8/Dz8PDw8PDw/8A/wD"),
        new Numeral(
                6, 0.8888889f, "wD/AP8A/8Pzw/PD8PDA8MPwP/A/8Dz8/Pz8/Pz88PzwPPw8/Dz8PDw8PDw/8A/wD"),
        new Numeral(
                7, 0.7777778f, "/8P/w//D////////hzOHMwA8ADwAPIAPgA+AD+AD4AP4A/gD+AN/AH8AfwB/AH8A"),
        new Numeral(
                8, 0.8888889f, "wD/AP8A/8PDw8PDw8PDw8PA/8D/wP/AP8A/wDzw/PD8PPA88DzwPPA88Dzz8D/wP"),
        new Numeral(
                9, 0.8888889f, "wD/AP8A/8DzwPPA8PPw8/Dz8PPw8/Dw8PDw8PPA/8D8MPww/DD8PDw8PDw/8A/wD"),
        new Numeral(
                0, 0.7222222f, "AAwADIA/4HHwYPBgeOB44BjgGOAc4BxgHHAccAxwD3APMA8wDzwPHAweDB4cD/gD"),
        new Numeral(
                1, 0.5882353f, "APAA8AD/4P/g//BwAHwAfAB8AHwAHAAcAB8AHwAfAB8AHwAPAA+AD4APgA/gD///"),
        new Numeral(
                2, 0.7222222f, "ABwAHIB/4OHgwODA4MAA4ADgAOAAcAAwABwAHAAOAAOAAYAB4ABwYBxwHHD8P/8/"),
        new Numeral(
                3, 0.7222222f, "AA4ADuB/8HBw4HDgcGAAcAAwADAAPIAP4B/gHwA+ADwAPAA8ADwHPAceBx4PD/8D"),
        new Numeral(
                4, 0.7647059f, "AOAA4ADwAHwAfABuAHMAc4BxgHDgMOAwcDwYPBg8DDz//wA+AD4AHgAeAB4AH+B/"),
        new Numeral(
                5, 0.7058824f, "wP/A/8D/cIBwgHCAMAAwADAAOAD4P/g/CHwAeAB4AHgAeAB4AHgEPAc8BzyPD/8D"),
        new Numeral(
                6, 0.6666667f, "AAgACIB/wPBwwHDAeEA4ADwAPAAMB/8/Pzw/PA94D3gHeAd4BzwHPA8MDwyPB/wD"),
        new Numeral(
                7, 0.7058824f, "/P/8//x/B3AHcAc4ADwAPAAMAA8ABwAHgAPAA8ADwADwAHAAcAB4ADwAPAAMAA8A"),
        new Numeral(
                8, 0.7222222f, "AAwADIB/4GBw4HDgeOB4YHhweHDwPPAP4B/gH3g/HDwPcA9wDzAHMA88DzwPHvwD"),
        new Numeral(
                9, 0.7058824f, "gH+Af8DzcPBw8HjAeMB4wDzAPMA88DzwePj4//j/wHAAeAA4ADgAPAQMBAyPB/wD"),
        new Numeral(
                0, 0.7222222f, "AD8AP+B/8PHw4PDg+OB44HzgfOB84BzgHPAc8B/wH3AffB98H3wfPBweHB78D/gD"),
        new Numeral(
                1, 0.6111111f, "APAA8AD+wH/4f/h/4H8AfgB+AH4APgA+AD8APwA/AD8ADwAPAA/AD8APwA////9/"),
        new Numeral(
                2, 0.7777778f, "gH+Af8D/4PPg4ODg4PBA8ABwAHAAOAA8AB8AH4APwAPgAOAAcGA4MPw//D/8P/8/"),
        new Numeral(
                3, 0.7222222f, "gH+Af+D/8PHw4PDg8OBg8AB8AHwAP+Af4D/gPwB8AHwAfAB8DHwPPA8+Dz7/H/wD"),
        new Numeral(
                4, 0.7777778f, "APAA8AB4AHwAfAB8AH+Af8A7wDvgOHA4ODw4PBg8/P//////AB8AHwAfAB/gP+A/"),
        new Numeral(
                5, 0.7222222f, "4P/g/+D/4P/wwPDAcEBwAHgAeAD4H/g/GH4YfgB8AHwAfAB8ADwPPg8fDx//D/wD"),
        new Numeral(
                6, 0.7222222f, "AH8Af+D/8PH4YPhgeCB8ABwAHAD8H/8/Hz4fPg88DzwPPA88Dz4PHh8fHx/8D/gD"),
        new Numeral(
                7, 0.7222222f, "/P/8//z//38PPA88BzwAHgAfAB8AD4APgAOAA+AB4AHwAPAA+AB4AHwAfAAcAA8A"),
        new Numeral(
                8, 0.7222222f, "gH+Af+D/8OHw4PDg+OD44Phw+HDwP+Af8D/wP3x+HHwfcB9wD3APfB8+Hz7/H/wP"),
        new Numeral(
                9, 0.7222222f, "gD+AP+B/8PH44Pjg+OB44HzgfOB88Hz8+P/4//B/gHAAPAA8ADwEHg8fDx//D/wB"),
        new Numeral(0, 0.6f, "wAfAB/Af8B88eD/gP+AP4A/gD+AP4A/gD+AP4A/gD+AP4A/gD+AP4Dz4PHg8ePAf"),
        new Numeral(1, 0.6f, "AAYABsAHwAfwBz8HPwcABwAHAAcABwAHAAcABwAHAAcABwAHAAcABwAHwAfAB/z/"),
        new Numeral(2, 0.6f, "wAfAB/x//H8/eA/4D/gMeAB4AHgAeAB4AB4ABwAHAAHAAcAB8IDwgDyA////////"),
        new Numeral(3, 0.6f, "wAfAB/x//H8/eA/4D/gM+AB4AHgAHgAewB8AfgB+APgA4ADgAOAA4A/4D3gPePwf"),
        new Numeral(
                4, 0.78571427f, "AD4APgA/AD8APwA/wD7gPuA+ID4gPjg+HD4cPgQ+BD7/////AD4APgA+AD4APsB/"),
        new Numeral(
                5, 0.64285713f, "/H/8f/x//H8MYAxgDAAMAAwA/Af8B/x/AHgAeAD4APgA+AD4APgP+A/4D34Pfvwf"),
        new Numeral(6, 0.6f, "AB8AH/B/8H/w4DxgPGA8AD8APwD/H/8f/38/+D/4D+A/4D/gP+A/4Dzg/Hj8ePB/"),
        new Numeral(
                7, 0.64285713f, "//////////8D4APgAHgAeAB4AH4AfgAeAB4AHgAHAAcABwAHAAfAAcABwAHAAcAB"),
        new Numeral(8, 0.6f, "wAfAB/x//H88eD/gP+A/4Dx4PHj8f/x/8B/8f/x/P/gP4A/gD+AP4A/gP/g/+Px/"),
        new Numeral(9, 0.6f, "wAHAAfwf/B88eA/4D/gP4A/gD+AP4A/gP/j8//z/wOEA+AD4AHgAeAB4DB4MHvwH"),
        new Numeral(
                0, 0.6666667f, "8A/wD/wf/B98fB/wH/Af8B/wH/Af8B/wH/Af8B/wH/Af8B/wH/Af8Hx8/B/8H/Af"),
        new Numeral(1, 0.6f, "AB8AH8AfwB/8H/8f/x8AHwAfAB8AHwAfAB8AHwAfAB8AHwAfAB8AHwAf/P/8//z/"),
        new Numeral(
                2, 0.6666667f, "8B/wH/x//H8ffB98H3wcfAB8AHwAHwAfAB+AD4AP4APwAPAAfOB84Hzw////////"),
        new Numeral(
                3, 0.6666667f, "8B/wH/x//H8ffB98H3wcfAB8AHzgH+Af8B8AfwB/APwA8ADwD/AP8B98/3//f/wf"),
        new Numeral(4, 0.8f, "AA8ADwAPAA+AD8APwA/AD3APcA94D3gPOA8MDwwP////////AA8ADwAPwH/Af8B/"),
        new Numeral(5, 0.6f, "/P/8//z//P/8/zzgPOA8ADwAPAD8f/x//P8A+AD4APgA+AD4DPgM+A/4/3//f/wf"),
        new Numeral(
                6, 0.6666667f, "4B/gH/B/8H98cHxwfHAfAB8AHwD/H/8f/38f8B/wH/Af8B/wHPAc8Hxw8H/wf+Af"),
        new Numeral(
                7, 0.6666667f, "/////////////w98D3wAfAAcABwAHwAfAB8ADwAPgA+AD4AP4APgA+AD4APgA/AA"),
        new Numeral(
                8, 0.6666667f, "8B/wH/x//H8ffB9wH3AfcHx8fHz8H/wf8B/8f/x/H/wf8B/wH/Af8B/w/H/8f/Af"),
        new Numeral(
                9, 0.6666667f, "8A/wD/wf/B8ffB98H3wf8B/wH/Af8B/w///8//z/4PAAfAB8AHwAfAx8/B/8H/wP"),
        new Numeral(
                0, 0.6666667f, "ABwAHIB/gH/g4HDgcOB84BzgHOAc4BzgH+Af8B/wD/APcA9wD3wPfA8cHw8fD/wD"),
        new Numeral(
                1, 0.53333336f, "AMAAwAD8APzA//D88PwAPAA8ADwAPAA8AD8ADwAPAA8ADwAPwA/AD8APwA/AD///"),
        new Numeral(
                2, 0.73333335f, "AD4APsB/wH/g8ODA4MAA8ABwAHAAeAB4ADgADgAOAAfAAcABIAAgABxw/z//P/8/"),
        new Numeral(
                3, 0.73333335f, "AD4APsD/wP/gwODA4MAg8ABwAHAAOAA4wA8AfgB+AHgAcABwAHgAeAQ4Hz4fPvwH"),
        new Numeral(
                4, 0.78571427f, "AHAAcAB4AHgAfgB+AHfAecB54DjgOCA4GDgYOAQ+BD7/////AA4ADgAOAA8AD+B/"),
        new Numeral(
                5, 0.78571427f, "wP/A/8D/wP/ggOCAIAAgACAA+A/4D/g/AHgAeAB4AHgAeAB4AHgEOAQ4Hw8fD/wH"),
        new Numeral(
                6, 0.73333335f, "AD4APsB/wH/g8PhA+EA4ADwAPADcD9wP/D8fOB84HzgfOB84BzgHOB8OHA8cD/gH"),
        new Numeral(
                7, 0.71428573f, "/P/8//z//P8PcA9wABwAHAAcAA8ADwAPgAOAA4ADgAPgAOAAcABwAHAAHAAcABwA"),
        new Numeral(
                8, 0.73333335f, "AD4APsD/wP/gweDA4MD4wOBw4HDgOeA5wA/gP+A/OHgccBxwHHAccB9wHDgcOPgP"),
        new Numeral(
                9, 0.6666667f, "AA8AD+B/4H/w8HDgcOB84BzgHOAc4BzgHPD8f/x/4HAAfAB8ABwAHAAfjw+PD/8D"),
        new Numeral(
                0, 0.73333335f, "wD/AP+B/4H/48Pjw+PA88DzwPPA88DzwH/AfeB94H3gfeB94Hz4fPh8+/A/8D/wH"),
        new Numeral(1, 0.6f, "APgA+AD/AP/wf/B/8H8AfgB+AH4AHwAfAB8AHwAfAB8AHwAfwAfAB8AH/3//f/9/"),
        new Numeral(2, 0.8f, "gD+AP/B/8H/w8HDwcPBAeAB4AHgAPAA8AA+AB4AHwAPwAPAAeDh4ODw8/z//P/8/"),
        new Numeral(3, 0.8f, "gH+Af/B/8H/w8HDwcPBAeAB8AHzAP8A/wD8APwA/AHgAeAB4DDwMPA88/w//D/wH"),
        new Numeral(4, 0.8f, "AHgAeAB8AHwAfwB/AH+AP8A8wDxwPHA8eDw8PDw8////////AA8ADwAPwD/AP/A/"),
        new Numeral(5, 0.8f, "wP/A/8D/wP/wf3BAcEBwAHAAcAD4D/gP+D8APAA8AHwAPAA8DDwMPA8//w//D/wD"),
        new Numeral(
                6, 0.73333335f, "AH8Af8D/wP/g8fhw+HD4ADwAPAD8P/w//H8feB94H3gfeB94Hz4fPh8+/A/8D/gH"),
        new Numeral(
                7, 0.73333335f, "/P/8//z//P/8fwd4B3gAPgAOAA4ADwAPwAfAB8AH4AHgAeAB+AD4ADgAPAA8AB8A"),
        new Numeral(8, 0.8f, "gH+Af8D/wP/w8PDw8PB4cPB48HjwP/A/wA/4P/g/PHw8eDx4D3gPeA88/D/8P/gH"),
        new Numeral(
                9, 0.73333335f, "wD/AP+B/4H/4eDzwPPA88DzwPPA8eDx4/H/4f/h/4HkAPgA+AD4APgcP/wf/B/8B"),
        new Numeral(0, 0.7f, "4A/gD3gfODgcOBxwHHAccB9wD3APcA/wD/AP8A9wD3AfcBxwHHAccBw4ODh4H+AP"),
        new Numeral(1, 0.6f, "gAeAB/AH+Ae/B48HgAeAB4AHgAeAB4AHgAeAB4AHgAeAB4AHgAeAB4AHgAfAD///"),
        new Numeral(2, 0.6f, "+A/4Dzw/D3gPeA94AHgAeAB4AHgAPAAMAAcAB4AHwAPwAHgAOMA4wAzAD/D/////"),
        new Numeral(3, 0.65f, "8B/wH3x8HHAccBxwAHAAcABwADwAHuAP4D/gPwBwAPAA4ADgBOAE4AzwD3B8Pvgf"),
        new Numeral(4, 0.75f, "ABwAHAAeAB8AH4AdwBzAHMAcYBxwHDAcGBwYHBwcDBz///z/ABwAHAAcABwAPoD/"),
        new Numeral(5, 0.65f, "+H/4f/h/GHAYYAgACAAIAAwADAD8D/w/AHwAfABwAPAA8ADwAPAA8AxwD3z8P/gf"),
        new Numeral(6, 0.6f, "wH/Af/h4OPA88D8ADwAPAA8ADwDPP/9/D/gP+A/wD/AP8A/wD/AP8DzwPHj4fPA/"),
        new Numeral(7, 0.65f, "/P/8//z/DOAE4AdgAHAAcAAwADwAPAAcAB4AHgAeAA4ADwAPgAOAA4ADgAPgAeAB"),
        new Numeral(8, 0.7f, "8A/wD3g8HDgccBxwHHAccBw4eDj4D+AP8B/wHzg8HHgPcA/gD2APYA9wHHA4PPAf"),
        new Numeral(9, 0.65f, "8B/wH/g+HHwccB/wH+Af4B/gH+Af4Bzw/P/8//DvAOAA4ABwAHAAcABwDDx8H/gP"),
        new Numeral(
                0, 0.6666667f, "4A/gD/A/+Hw8eDzwPPAf8B/wH/Af8B/wH/Af8B/wH/Af8B/wPPA88Dx4eHzwP+Af"),
        new Numeral(
                1, 0.5714286f, "gA+AD8AP+A//D/8Pjw+AD4APgA+AD4APgA+AD4APgA+AD4APgA+AD4APwA//////"),
        new Numeral(
                2, 0.61904764f, "+A/4D/w/f34ffB98H3wAfAB8AHwAPgA+AB+AD4AD4AHwAPAA+MB84B/g////////"),
        new Numeral(
                3, 0.6666667f, "8B/wH/g/fHw8eDx4HHgAeAB8AHwAP+Af4D/gfwD4APAA8ADwCPAc8B/4PHz8P/gf"),
        new Numeral(
                4, 0.7619048f, "AB4AHgAfAB+AH8AfwB/gHvAe8B5wHjgePB4cHg4e/////////v8AHgAeAB/A/8D/"),
        new Numeral(
                5, 0.6666667f, "+H/4f/h/+H84cBhwGAAcABwAHAD8D/wf/D8AfAB4APgA+AD4APgMeB94Pz/8P/gP"),
        new Numeral(
                6, 0.61904764f, "4D/gP/B/+Px88HxwHwAfAB8AHwD/H/8//38f8B/wH/Af8B/wH/Af8Hzw/Hz4P/Af"),
        new Numeral(
                7, 0.6666667f, "/P/8//z//P8c8A9wD3gAeAA4ADgAPAAcAB8AHwAPgA+AD4APgAfAB8AH4APgA+AD"),
        new Numeral(
                8, 0.6666667f, "4B/gH/g/eHg8eDxwPPA8cHx4fHj4P/Af8D/4fzz8PPgf8B/wH+Af8D/wPHj4f/Af"),
        new Numeral(
                9, 0.6666667f, "4A/gD/gf+D88eDx4H/Af8B/wH/A/8Dz4/Pz4//D/APAAcABwAHgAeAw8PD/8H/gH"),
        new Numeral(0, 0.7f, "gD+AP+B7cOBw4DjgOOA44BzgHOAc4B/gD/AP8A9wD3APeA84DzgPOA8cDw+8D/gD"),
        new Numeral(1, 0.55f, "APAA8AD+AP/4efh4AHgAeAB4AH4APgA+AD4APgA/AA8ADwAPAA8AD8APwA/gD///"),
        new Numeral(2, 0.75f, "gD+AP+B54PBw4HDwAHAAcABwADgAGAAMAAYABgADgAHAAGAAMDAwMBwwDzz/H/8f"),
        new Numeral(3, 0.75f, "gD+AP+Bx4OBw4HDgAHAAcABwADgAHsAPgB+AHwA8AHgAeAA4BDgEOA88Dx6/D/wD"),
        new Numeral(4, 0.75f, "AHAAcAB4AHwAPgA7gDuAO4A5wDhgHDAcGBwYHAwcDx7///9/AA4ADgAOAA+AD+A/"),
        new Numeral(5, 0.75f, "wP/A/8D/wOBgQGAAIAAgADAAMADwD/gfADwAPAA4ADgAOAA4ADwAPAccDw7/B/wD"),
        new Numeral(6, 0.7f, "AP8A/8Dn4MBwwHAAOAA4ADgAPADcH/w/HHgceB9wD3APcA84DzgPOA84HBy8D/gH"),
        new Numeral(7, 0.7f, "/P/8//9/D3AHOAMYABwAHAAMAA+AB4AHwAPAA8AA4ABgAHAAOAA4ADgAHAAMAA8A"),
        new Numeral(8, 0.75f, "gH+Af8Dx4OBw4HDgcOBw4HBw8DjgH8AP4B/gH3g+PHgceA9wDzAPMA84DxwcDvgH"),
        new Numeral(9, 0.7f, "wD/AP+B/cHA44DjgPOA84BzgHOAc8Bx4+H/4f/h3ADgAOAA4ABwAHAAPAwfvA/8A"),
        new Numeral(
                0, 0.71428573f, "gD+AP8B/4Hvw8HjwePB88DzwPPA88D/wH/gfeB94H3gfPB88HzwfHh8evw/8B/gD"),
        new Numeral(
                1, 0.61904764f, "APAA8AD8AP/gf/B/4H0AfAB+AH4APgA+AD4APgAfAB8AHwAfAB8AH4APgB/8//9/"),
        new Numeral(
                2, 0.7619048f, "wD/AP+B/8Hnw+HjwcHgAeAB4AHgAPAAeAA+AB8AD4AHwAPAAeDA8OB48/x//H/8f"),
        new Numeral(
                3, 0.7619048f, "wD/AP+B/8Pl48HjwcPgAeAA8ADwAHuAP4B/gPwA8ADwAPAA8BDwGPA8enw//B/4D"),
        new Numeral(
                4, 0.7619048f, "AHgAeAB8AH4APgA/gD/APeA84DxwHngePB4cHg4e/////////38ADwAPgA/wP/A/"),
        new Numeral(
                5, 0.7619048f, "4P/g/+D/4P9wcHBgMAA4ADgAOAD4B/gf/B8APgA8ADwAPAA8AD4GHgcf3w//B/4B"),
        new Numeral(
                6, 0.71428573f, "AH8Af4D/wPPg4fDgeAB4AHgAeAD8H/w//D8/eB94H3gffB98HzwfPB8ePB/8D/gD"),
        new Numeral(
                7, 0.71428573f, "/P/8//x//38PPAc8Bx4AHgAPAA8AB4AHgAPAA+AB4AHwAPAAcAB4ADwAPAAfAB8A"),
        new Numeral(
                8, 0.7619048f, "gD+AP+B/8Pnw8HjwePB4cPh4+Hj4P/AP4B/4Pzw+HnwfeB94DzgPPA88Hx/+D/wD"),
        new Numeral(
                9, 0.71428573f, "gB+AH+A/8H/weHjwePA88DzwPPA8+Dx4/H74f/B/ADwAPAA8AB4AHgcPzwf/A/8B"),
    };

    private static final long[][] TEMPLATE_WORDS = words();

    private static long[][] words() {
        long[][] result = new long[NUMERALS.length][6];
        for (int n = 0; n < NUMERALS.length; n++)
            for (int k = 0; k < 48; k++)
                result[n][k / 8] |= (long) (NUMERALS[n].bits[k] & 255) << ((k % 8) * 8);
        return result;
    }

    record Family(Reading reading, int left, int top, int right, int bottom, int frames) {}

    /** Complete finite extent family. A measured-C contact may hide a glyph cap beyond the residual box. */
    static Family family(
            byte[] raw,
            int width,
            int height,
            BitSet curve,
            int l,
            int t,
            int r,
            int b,
            float gap,
            ObservedFringeContinuation.Charge work) {
        if (raw == null
                || curve == null
                || work == null
                || width < 1
                || height < 1
                || raw.length != (long) width * height
                || l < 1
                || t < 1
                || r >= width - 1
                || b >= height - 1
                || l > r
                || t > b
                || !Float.isFinite(gap)
                || gap < 5
                || gap > 100) return null;
        boolean left = false, right = false, top = false, bottom = false;
        for (int y = t; y <= b; y++) {
            work.tick();
            left |= curve.get(y * width + l);
            right |= curve.get(y * width + r);
        }
        for (int x = l; x <= r; x++) {
            work.tick();
            top |= curve.get(t * width + x);
            bottom |= curve.get(b * width + x);
        }
        int margin = Math.max(1, Math.round(gap * .30f)),
                ml = left ? Math.min(margin, l - 1) : 0,
                mr = right ? Math.min(margin, width - 2 - r) : 0;
        int mt = top ? Math.min(margin, t - 1) : 0,
                mb = bottom ? Math.min(margin, height - 2 - b) : 0;
        if ((long) (ml + 1) * (mr + 1) * (mt + 1) * (mb + 1) > 4096) return null;
        float[] scores = new float[10];
        int[][] boxes = new int[10][4];
        int[] independent = new int[10], observed = new int[10], models = new int[10];
        Arrays.fill(models, -1);
        int frames = 0;
        for (int dl = 0; dl <= ml; dl++)
            for (int dr = 0; dr <= mr; dr++)
                for (int dt = 0; dt <= mt; dt++)
                    for (int db = 0; db <= mb; db++) {
                        work.tick();
                        int ll = l - dl, tt = t - dt, rr = r + dr, bb = b + db;
                        // Every independent residual core remains inside every tested complete
                        // glyph box.
                        // Additional samples are original measured pixels; C is retained, never
                        // whitened.
                        var value = inspect(raw, width, height, curve, ll, tt, rr, bb, work);
                        if (value == null) return null;
                        frames++;
                        for (int digit = 0; digit < 10; digit++)
                            if (value.scores[digit] > scores[digit]) {
                                work.tick();
                                scores[digit] = value.scores[digit];
                                boxes[digit] = new int[] {ll, tt, rr, bb};
                                independent[digit] = value.independentInk;
                                observed[digit] = value.observedCells;
                                models[digit] = value.modelIds[digit];
                            }
                    }
        int best = 0;
        for (int digit = 1; digit < 10; digit++) if (scores[digit] > scores[best]) best = digit;
        float runner = 0;
        for (int digit = 0; digit < 10; digit++)
            if (digit != best) runner = Math.max(runner, scores[digit]);
        int winner =
                independent[best] >= 24 && scores[best] >= .56f && scores[best] - runner >= .07f
                        ? best
                        : -1;
        int[] box = boxes[best];
        return new Family(
                new Reading(
                        winner,
                        scores[best],
                        runner,
                        independent[best],
                        observed[best],
                        scores,
                        models),
                box[0],
                box[1],
                box[2],
                box[3],
                frames);
    }

    record Reading(
            int digit,
            float best,
            float runner,
            int independentInk,
            int observedCells,
            float[] scores,
            int[] modelIds) {}

    static Reading inspect(
            byte[] raw,
            int width,
            int height,
            BitSet curve,
            int l,
            int t,
            int r,
            int b,
            ObservedFringeContinuation.Charge work) {
        if (raw == null
                || width < 1
                || height < 1
                || raw.length != (long) width * height
                || curve == null
                || work == null
                || l < 0
                || t < 0
                || r >= width
                || b >= height
                || l > r
                || t > b
                || (long) (r - l + 1) * (b - t + 1) > 16384) return null;
        boolean[] visible = new boolean[384], ink = new boolean[384];
        int independent = 0, observed = 0;
        for (int y = 0; y < 24; y++)
            for (int x = 0; x < 16; x++) {
                work.tick();
                int xx = l + Math.min(r - l, x * (r - l + 1) / 16),
                        yy = t + Math.min(b - t, y * (b - t + 1) / 24),
                        p = yy * width + xx,
                        k = y * 16 + x;
                // All original samples remain in the caller. Only likelihood terms at independently
                // observed curve samples are uninformative about the overlaid digit; no paper is
                // invented.
                ink[k] = (raw[p] & 255) <= 165;
                visible[k] = !curve.get(p) || !ink[k];
                if (visible[k]) {
                    observed++;
                    if (ink[k]) independent++;
                }
            }
        float[] scores = new float[10];
        int[] models = new int[10];
        Arrays.fill(models, -1);
        float aspect = (r - l + 1) / (float) (b - t + 1);
        long[] visibleWords = new long[6], inkWords = new long[6];
        for (int k = 0; k < 384; k++) {
            work.tick();
            if (visible[k]) visibleWords[k / 64] |= 1L << (k % 64);
            if (ink[k]) inkWords[k / 64] |= 1L << (k % 64);
        }
        for (int n = 0; n < NUMERALS.length; n++) {
            var template = NUMERALS[n];
            float ratio = Math.abs((float) Math.log(aspect / template.aspect));
            if (ratio > .45f) continue;
            int intersection = 0, union = 0;
            for (int word = 0; word < 6; word++) {
                for (int q = 0; q < 12; q++) work.tick();
                long predicted = TEMPLATE_WORDS[n][word],
                        visibleWord = visibleWords[word],
                        actual = inkWords[word];
                intersection += Long.bitCount(predicted & actual & visibleWord);
                union += Long.bitCount((predicted | actual) & visibleWord);
            }
            float candidate = intersection / (float) Math.max(1, union) - ratio * .15f;
            if (candidate > scores[template.digit]) {
                scores[template.digit] = candidate;
                models[template.digit] = n;
            }
        }
        int best = 0;
        for (int i = 1; i < 10; i++) if (scores[i] > scores[best]) best = i;
        float runner = 0;
        for (int i = 0; i < 10; i++) if (i != best) runner = Math.max(runner, scores[i]);
        // Reuse established digit confidence thresholds; do not fit a threshold to a score page.
        int digit =
                independent >= 24 && scores[best] >= .56f && scores[best] - runner >= .07f
                        ? best
                        : -1;
        return new Reading(digit, scores[best], runner, independent, observed, scores, models);
    }
}
