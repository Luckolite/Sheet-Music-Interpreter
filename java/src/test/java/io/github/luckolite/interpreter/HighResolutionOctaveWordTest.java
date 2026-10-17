// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import static org.junit.Assert.*;

import org.junit.Test;

import java.io.*;
import java.util.*;
import java.util.zip.GZIPInputStream;

/**
 * Original OFL Bravura numerals with licensed Cambria/Georgia Italic suffixes. Held-out sizes72/88
 * and ratios.45/.55 are absent from the added64/96,.4/.5/.6 templates. Original typeset layouts
 * also vary numeral/suffix separation and vertical offset. No fonts or score pixels are bundled.
 */
public class HighResolutionOctaveWordTest {
    record Glyph(int width, int height, int shift, String encoded) {
        byte[] pixels() throws Exception {
            try (var in =
                    new GZIPInputStream(
                            new ByteArrayInputStream(Base64.getDecoder().decode(encoded)))) {
                return in.readAllBytes();
            }
        }
    }

    static final Glyph G0 =
            new Glyph(
                    59,
                    34,
                    1,
                    "H4sIAAAAAAAC/5WVXUhUQRTHj7Wbm4ofpEmuZS26pkQSaWWIfVtZpqYJCQk+ZJQ92AcRGQuFRvkR0kMJQYVpmJpkFlaYlJZJiBSBgWGahhpoJmW5pk5zztw7u9tDe52HPed/5v+7O3Nn5g5jtvY6L8m8cJ67MfJw5QSbRZu6uhxszbdkRjPZuQqJ+TnlbbUWX0wTJjWS7WRf30fiSwSKbJ5F8ViOpVyehAlr9S5/Q3Bac/1iIUcXodmnX3nQC1RzP/D6HoBCrEwWAcRjMrwJllx6eMPEDcJ7iCZ4XA4iCOV5njxTq40AT3n4Hg4bxnhsAPCm+g83Qm9LdDPKJJ4MAOynShGsxpAMgaMYPwHspPpd8VobJBqDciNPZgwUGFtDvY/U578DyKckT6CFEg1AmYGZGUIxtEIchlhYMEWGCoDnlBwVaOAvhXxDshjTOPDEsFX3nv/2AxwQjiOgF+ZzykbY+0d0ZKBwH8E0C2CcsTo4heImQIVwmGCtSCrVTZQyjvIx5bnUdQGgm/0MWkYdxwBaqdoEcELZSnIDruxmrMUHs8Rp6roD8JJluzSRSAfoxTgTDVCrTC5Zsl75FgOtjDLvVoDqRpccIVIVtMAI8FVBu/Tg0HzK1HfNF9YSEPZbfTnwhIf7xgIwydU47UAmDMoOvrB617eK4EsS0zeQ59FyELaohm/7HNB7dgfDDHBFzSdC6HjVs3Dw76AFHj7j5Thevx4buh1SbKJnt5tnaicbdeEmnFMNHTjveLv5Rlo1HdWyOWhe8Zm1L7WxpVrIGiLX4Uka3ibRcA2k1YhOQ5f4PuVIdsg5WkrGs6osUdEW5+gOMn6UOldBHzhHg/+ZmdUk0GbnqCvtHrtCpkB7naP+6Eu3K2SJUz/tHI1FY7RdIcrxvf2nFaPRY0TqEZqB76AGdIxGfFLqRPrTOk37sF7HrbqL4nYaow+c7pbG66bKA+0x1191VFloCCFNmi+53jT7b0ToZets7taha5kRRne9nzkpv0079RcavB4G1gcAAA==");
    static final Glyph G1 =
            new Glyph(
                    59,
                    34,
                    -1,
                    "H4sIAAAAAAAC/5WVa0gUURTHj7qbWyvqko90g62oNSOSSKNMI8I0JB8pBfVBMqIII5YSy7YWCo0eKhJlQh8Kk7LSwEQKM6kslbAoigpFtAflhywk29LU25xzZ+7MhBvTfLnnf+f8mHPuPecMY+rTWZLtjJhmtcfvrvvF/uMZr1oI6hNWOWmYfL0Uiemu2q6bnjA0M8YMkt3knviexMc4FAXGyG9R6Gz7IMsHqALeGEJ3UYL7hHagPGaE/D6D0MtiYy3KbCPoNX6st8VGEso1RtASjp4WG9Eo84ygezg62yvrJyTLjaBH5ULI+c11HgrrkBG0Timi3B8o75DtNlZKogCX9DHWbkMra2Iqz358tUG7s1GwIaUeC92Md+qvDOYAlGk3esyge2w1PiM8C9Ct2zioIzM++07OBaG6VL5u0qEN/ziXZMjQqC+HQvTxhvf7JEct2vuup4YLTdfkGz/qC+0EeCpEjT86L37HuueobDV/lyCZtWi4JSMWjTJM9X5K8KwD46yeyBXDGPg6gS6S+zhTruyxMoB0fo2ZrJhc9o/acbH08PnkEuwgZ+8pXdwK0IJrJFS47Y0jLVYIrCbHw0r0lQrazvUngC2MB7oMl16AYgeOoW0A68mxV2TultFbXE5a5LZdzrv5ojT6XqBxCmC+JjM6/HkcfShrJ8Tg0gGpJHcAnCSjHCCQqkdz+vkcHZBlKgTjkmJ6STIWIvh0LwSIRL+tGnQn7/oJVUqN2AhFpIb8lNmXAQGr0XGlBk3Qn9txgD424phLjcyaAO7yuwiBVeXoGKQOhCHKIEzU/xWAR6zAr03pkgCvcmnnhiniQoFm0Ucbhe4AuNHq5xK1H8ONNIjysiaT5Go6wf9OwzTgTJfU+KWL9UTH/hS1T7fLmgGuSsv1IHRPuvD42XUPhbCgTZO6dLHmwOdq7c/Exnhlg720MbBZOyNiKvRd4wQ4o9hSIUNmQ3OR1f+I8g8dPJ8fZ7eaw53ZpV1/d1ka5KojLPFtVXKw2bGdCuoPMSQpWNYHAAA=");
    static final Glyph G2 =
            new Glyph(
                    90,
                    34,
                    2,
                    "H4sIAAAAAAAC/62WW0yURxTH/ywLy01cNEIMSJsN0qAiXpqKhnip2EorSVvRB1GiFVDRPmw1UaPGNLaKS0p4kqYgEgiGYNJsRaJrGy8YiT5oNKJS2yAqaLUqLgq6Lu44M2e+b5fNvvjVeTlnzjnz2zMzZ85+jAWOxq1/erwdexrVtLe6vjvAO9RWbGfGRjmAUfHAdJoOpPD5h8t3/dLUXFu5u2hKOJBqDHw4DDQ20LwSwSO8zRD4dKQG+IMM2cHg0ceEeTfXEhnryIsdXzbI2OC+jJjMozrlyJdJUWnL21sn+MGdVg2Q4JWGnmDw5FvS7j0FLGWVJmHaxnoyhLTcJsjjBUjdf6zOxk06uC9VJxSRxTGSG+/waLHRqKq2Njx3mZD2OO2L6/1LgYPS8WwS5rm5PA5YteCBaX6Gk0wzA7kpWx7qSfwLHEi8xpXpsOQV+hhzAQ7p+Rop/UJ2A3kq2PuZHxI7JE3/cHVBbrrVPCYr397uC7iQNv5Dx4UyA/hEbOQk0KgcVLBXgZ9U8JqA9ArItBeYEfqqdwKlUrEiskvIGkBsgc3F2GHpaALOUKy47rXrFfkw2bKAn0OTF8LcJ+TfwLfSsA4JYk/3gFUUUYYI2ngdRMweAkc+k7YurvaEBA+PwiKpNACnpZKJfCEOAU0UYsMsKV1mYLGX5RBZHf0P/AxDp3wF2CeVUkTLcnkUhgoh7UCHdPCy3Cykexw/0efsgYnINbR+MlAemnxAS3US5kjZAlwUcoXapW828JtQbgK2B4xVE9hE1XWNqxtri6cmRY7JXFzSMhhAXoXwF0I+DQM1qA2Ik2+rQJEdyQBRDm7hYJZL5Hm0fNfIZ5JY6yenYaqUrUCzVD7CfHVvcHHhTHbAFpDJEzMxqmiaTrO4CI29VQt8BJRIZRsg3/R9YLVWazl37/8Yd64ECwPIhxThjlbqQHLVXea5NF85TqrA37WnnCP6EgEndomqezVRxEW38gtIujysk/Np/cc0q+DqMjcV2RzyLFKBPNVOIT1RWCINxcLbLrTbS2LiC26wftGNG/S+YaH1e2laD9i1B32UPOYhY12/We35puojzrO6q1e5uo2Rl9HqjBAujyJ3GgK/jKXVO0L4Hiryf4bITrX6Ep2FIzerVG8a59U5+wyRi2j1B7SBubLmtBzVH22mIfDrBFpt1zqzGNV605RjuyGySx2GrEqWOOLV9alO1WGIXKq6wxv1fOX4lXx2muUYAr9RWdJ/UKf6uHhK3wZR/yflsyrLE3LmltsfRz3YO5tcG409k3W0Osbjv7LCfrraleT6fPidobcqvi/M1r7nctfuqOdN/YJJayh/fUqOrwbePd3U4E8s8QprwhCxpq6l/BsqC8t+Q1UREfTn0So/HtP9lqjvetl7HL4Tm2YmWeJs2WVOt0HEW+lOp0v0CwAA");
    static final Glyph G3 =
            new Glyph(
                    90,
                    34,
                    -2,
                    "H4sIAAAAAAAC/6WWfUzVVRjHv5d74YIX8WLzktNBc4QjQ1JaI8KyvKU0cb5k/1BMESm1WtdYwIi55qYIyvijoAUqg2mMrQ1fWEIvpltUW245aJI1JQf5UmqgUreLnM7vPM/53csv226X88/veTnn83vOy/OcI0Roayv7zB/o3dnG6lBjy4UQ71hXsU9E1qoBTE8AFpE6OlfqD7xY9cGh9ua6HYUP24HkyMCHbaC2hfQ6WJu9KyLwyRgN+JQM2VbwjOMRgfvdGpAYUIZBK3jB+YjAw8kmoZAsNZO5CTX+iMCjjwQZnWTKCuXOLb0a2eYFngtCXGPK9LMUn/amuR0zM/N9pyciPG5iY0h4L5BpF7BYTLntkMBNrzL5MNkygX1TBh+QvCKxk8AxfyjbgBQHpwrudgArAiKXyHlkfBd4bKrgkVlyRW+Jy1FEbiLrAqD6f6MuGoCVpnoOmHdZiEYCR9Hp6pPitubihUkxMzNWbO64Eyb6ylpgb1DdXyrBwkvkp8hWNTlNPM1hot8DvrOYrjuIUU9qGmnx0ZpdFh75TbjvWkwHmfCL0s4a4pz6S8J/Zik7esIiL0G+1ZRP4x8lrVaK60eUOJ5DnmfDAftj/5UEo04av4vUFsCnE/ooeRxjYZC/Bs5YTO0853NcRzpPma4hdl0ISViPEL15rtlb5Zm5szt9WsZR9u01lvlLb8L9ZeN6+HoanX6vGTK5X1evL4B1ok4d/3IxmG58nRfJtwarRIXq/Rb3/tNFoyvvQb7K5N9MSxzqG92tt7qjkPp76vM/3FwH7CdPEuoq5xy53eOCk/t28mhapECNN7PELBpf8TqbhfQK0ODpk8IiOPMKpLkbqFGen4CKlEtS2ABw50IanUITeFKdOR0jX7QZZshd8iL4xBAWy9piXDQ9QJs+unFn+T6ivn8n0mifrsxGa2TQMlIrTPI7QIkS3IgZML5NQJ8yFAN7lLBPk7t5MU4rzTMp64a5UvWa5GVwDPPci5ThFSTSUqXD85cSSjW5hKuDSsxr/JsPyecjLdcEj0/nrGkFTiohgxPvug3bdd7Z1fcuR0lz7OfHxQ16G8RaQ/4e2M3xxKnr/JoNtcpwTD9VxmfgCSWc4ihPUL1W059F4QQeJ9e24Cls0KE+hBz17QC+VUI57JSonwPv8zqpNs0f3LKCm7S1L5Fr+XiQ/DLst43vDRvv+BbEB7gczacuyzF77Hzt9oJs/Z7zbqpskYO+kUG3qh4/PkOO1aMhmZOKhXru7UqYj6W6HGXpY/mRSLY+sYwsbLIheuOBjuq1dCyce0JTUm7wZp47VE7/CmzQ5eg+Y9r9iXhD7kK05fI4ph6PaUFL7OtDk5L9iE7lXKMuyXYIeHBgQpUjYNXHXW+7oqr+++EzceK1rCRn/LzsrZ0jFlc51yY595U6PSgT1uQMNCxJiE4pUmn4D+pAMXz0CwAA");
    static final Glyph G4 =
            new Glyph(
                    87,
                    42,
                    1,
                    "H4sIAAAAAAAC/61XfUwVVBS/PD7kAfpeSPXSxwumzFwxdAX5UUu0cujmXCT4h5sJFcQwzRxhq0xq2sq1RWUKiYFOXaaUzcqKNbVAy2jR2sucWCBIySBkIp/vds899/PZP73H3dg7v98598d995577nmU2mO4+f2tZU+WvrD9syt0vEb/7gcmEDXSK3vHQ/T6y/HEHkkHw1c9PQ218j5sGej+Jh/B88LZ+Hhy/F07AjK2MdOZ3WZOHjq4crozJunep+qH2mtX+/5QjoYErpNySuCaaI6rwf5yDv6TKuH7283A/YZq3RT1DZ3szz0mHS2xnIw6o0KLOJHYQ+lhOWWecB3l6IKM7FgC63mvbbizJpl7lkjP6D04sUyvoA2Ztymt3NbZtxfsqcJ1jHuOCnQxhYFHB3BSIni2So09Yj0NxjfzcCZHoBnMvkPYfZPAs1+o+phdLLf9GfDIjaSLhGy3ITuXM2kC3c3sXGsVR7g5ksnMRaPSU8XQhEEBrjhQNXLYkE3n1GRj7a8r38MMfc2tTcya1KUclQzeJ0GTPJTfDFkXZ2aLwwf7e+VrZsgPxq+woNf0pBUMlkvwkZQtp9ZMSGNEn8CiRrU3gUTyQ1rFeNeA5m9j+JgEu6Vs7DkVsAGZfYjWmXnDxhQ8v9ZIxhdp+jyDDnXnj6hsTpYXpMPNsW8E4Z3W1lLqJI/Bx2aIOalpOMt0hX7WdSDVj9RShHsQXba3lvYT8gF8zoaDH9J8AcMlCo25ta4L0nxsLYJnRcDeoK39jkRDLvY7jLsHI03nM4xSo25FrL/atQLNfJnlq4O2djNZBh8/QNAaTf8F2KhBrXFmQYzGNHa8pEqGN2hrM8ghmR9ko6Yho243K9u75IYx70fl9QNu1NHHiYffpAPAv6p5yJdVVrmdE6y6K6CdO6G46ewMzCe7uPExBG7RcXDDd5iqv6cGqcb1UXvrUzR8h8zE4zsBket1fkAWNxmihVE37MHygH0lUxX6KYF8IWqJWeMorQfYIdE/RQ7yH+MVOx2j5OU5dysplQ54qOKvCnCKV5EWefm9KLPgeEWMKevQX+cto14ccpGFqtC9aDh2ejIALbsw2FRA6bf4iJE1LPaM19RdrGQv8Xe+sPlaz6c5rPDpbb/MC/pa/+CluqyJp9+QMzNp+81oPYf1b4Gpe15NrzDYldeMw6xRtKeZ9k7FerWui+YiOUuUlJESQ6Bap1SZml1r9wGVYuNy/oQHKPcW58xNnZT+Ip6FsyruzQglW2xMP1sw3Rnre6T2enB/4S9Ji3NlH7bJchR4yKCqlWxeyL3M0uCvK9LJfu//95iBAh0m1ztRyBaGLIvNTEzAIucL2S0hyybx+V6bFH0dqQ9ZFpuBmFGLXCj60KGQZZ9GhVarI8cOgWwIvas9gQrbTK4OOW9PGN3yg1zCbUhcxMVGNITThPtRZLnax/YsfM/2hdfcN2IFm/U5P7a+CnwsE78K90eDXzxjN2XnL/ZhQXAUd4f/Y2Ssdq71LHg2+sfpF5m/6omsaZOjEpIz8rafHAlf719hFTRDRg4AAA==");
    static final Glyph G5 =
            new Glyph(
                    86,
                    42,
                    -1,
                    "H4sIAAAAAAAC/61XfUyVZRR/uMAFriSkNlAvJGJZS4dIMQxdSCZTV5lM6WtUYumMoqTQlUujFa0oJ2mJnxHWmpoNnJYZtSgRndEy66amNj+SCcMARbpyeXvOc57P+97t3g3fv57z+53nd9/3POc551zLMh9vy8a3yp4pXl65p826Tk/3pnuiiHzGV126DppXVw4i5jPs8wGLNqei1LxtR3ravy9A45UBijbEMplRP3J7cySzNwxI9Eg0E4k4KJGFDBjSEWxn59E91a8W5qROtFN9d+IXlynoDCIfBBH9QxzCY3ZuC6caNCyRITOCqPZfaC5ljuvs3L1ctV3DJjHklhDC9wg4/m6D2xwoGu7VwPEMGhqC6nLw67fBB0Rw/tTAOIakh6D6OPV70A7vEKrLFNbC0zcE1VupX6Ud3iRUo49JbAkiW4OLtoFfsx3fKS9p0t8cOh/P7ORrwVXrqJ/La8d/VXc/xYPQLDS3hBCApdQvNwDui1eycfUAPIdGaSj3cjJ1XBGIKNYqVdgLXa1zcVnQH4KoFy77t4GYUy69AkZi+jpe8wm+aUHSoHEfyt9ouitm6hlZ7KB+XLZ6Nk5PiHI/tEOXXUtsz90/C/KbLETWc/siBGyKYN+jRqZV5+bbcvR6lOUvWi3f7Av5OxyoZ9ZJbuXTdcmLamN6jxQ9nuIn6uqUXFXFP521gI3kwG7mUc+t4XBfyfBV53pPVrj0Iz5eFGELwGzzpMZS6DZRUgeDw2f8SLAOdmOM4eSiWCP9d6GDBHjeMFQzKJJvFM6duN4KafO2YJ4HpgouPI9zzt5yp67qOGD5Vdt3pDVdJdNiupwpiUaWkJb1EzYs8hS9cwfdumyeJnoRgENG6eGXMI0u35REB/iNs87ehBJLcXOOLnvCvOuD+5QdS8LxqLvCKfOdIpzsVPNRYAKvItcWa6pagy0xvtOyRoij2wfF/rIiEmCO+I03gcMSfj9Mqi5SzncYYbWsGPIkLlZSQm+vkFs3L8P992n4BqmqKvYFM6xWNyEfq3N7VisK4Jg9i9iHiflCVX1yrV9Y95NIbJw+SN1PFXEC9i0Yi/vP66qXbuCqRRJ6wi+sK8gDWmU+rYhtYG/GkcVp3qNsrvq6RNx+YU0j23HxESUSNeJlSPTWYWy726yLfHQjXwrAA1aTcthLEntVe52jbR3DBhNs+s4+QzWXD5r/CWAdlFBVivqzSTVfjqbMu36NuRHvLSGnjOKOkwBZYrSKUcphDbmdv0Yr+O1XzKPUnGxZP6BAha76CWJuVX6h4aRI65dY8rVWep29qqvSXI+EYj+NKcRrBfw0vmpYg5lrEWKeP5ZAigUBg1uWmtYzxKF6UGO2jOHZTOxd+nixWptrtseRXNn88yA0YmTwFVJrLqZTE9asCV+xUHWWY18csk+PyTn2J6So5UrHrhk0cKpNHIJiUoRNs+t+ui4U5+7hLevGqQV5yVgEHIvazVwr12rOw1c0ohaaSHpdh/evVSNpiMtV4vtqJhlNIPElj232LZNkjd+flAy1MfuoyXnWP52ZOjQiNiltXmVjwNHq8PwxMdHJc2qu2pjG0ikjXOFxE0tUhv0PeXfJwRwOAAA=");
    static final Glyph G6 =
            new Glyph(
                    127,
                    42,
                    2,
                    "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");
    static final Glyph G7 =
            new Glyph(
                    126,
                    42,
                    -2,
                    "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");
    static final Glyph G8 =
            new Glyph(
                    85,
                    34,
                    2,
                    "H4sIAAAAAAAC/61WbUyTVxR+QD5aKQx0arIZVLKQGEDGUMOMOmF14yMkmwE1czZxYrcJGkuYxBA1i4ljsDl/bCtTFCKROLIsxMmCbgmCEz8SR9iIbswY7EBE40crorWld/fec9+2VP68ZOdHzz3nuc/Tc79fxoKtqfJXt6d7X5MKB+2NN4LQsbYSm+ePpgpzPmMDRbHJvTx3KtO4RcNvl883rHSM1q+boFkNIDYOyKDQNZfH89furjt+ov7AXkvqNCCxISkK2MMuzeRQCWNfcIdb1P3H+GXnHrXkm1EWrNkcBrKPKT6AUJvWxlgPcOHqvOY7RthYXUYvlx2Svb+CxctdDvBzkGZHlMb9hRJZoZovnOLZ7zHTmXGJuSPRciHrAatDgk/OHLI9wq+H8UlAsy9e4yZIlA2Eaqb0i7QF75X9wEtAuGPFIGPFKBLZfmOMQ7JWoSCgOZToJ1soUzNRMq7GLbLjL2LHVu53Ymn5Gca8CTgi0mY+0cLccfjWr+l6NUBvpVRmsOTcihHKnkdY3ij3qSgq5a4L4OWyThjvSfgkMKBpet4K8GPGZOo6b2abk+MjZqQX2rp8Ws9dAB88cwAL7nJfiUUiuw7FEvVlIsVf6KagooootR94jT1vaVginB34jMKd/Ndrgl2iXwOfaD33cq3NHynRZsqlA18+r3kTaBC+EHEuKriDu6tAu8he5hvqrOp5lEt9wPaRZtRDmfsLQbMTsG9gcHL3NAbbRXgIpmejbawbOM6jHusexHrGfxfI6Qgg18OWk2gesT8Flk4y+jysEa4d6BF+LQqebuwT9adcH6nNeZyPXFbFTwhzzuKz94gNh5PoYWKnANWTiBpodrZjoQwXIGn1Re5XCGaOk72EDOt3ArgGJA3LqRcWTlvnT94srS9ZNCdqRlrulpbHmiiixVSyV1Alw3eNBf8I/2+uKdXOT+n66dm/UccjFcNy+0p7g3K7J+782fVsKnYvgugHKUymyBSpyVZORbRBkW/KqFc0Xz7oYO4rqxRwZgqihURdTFEtbxY7ZdO7jJDV+jVd0UTdT2EjYNNO5klCIsZ0i55Qg7ym7oLWTj80qKAbukWLibhwEsitRPv0aj6JIWLVJNiIEr2rV7RVEa/Q4GvM6Vb/wT+v5tSnV9RCxHlU9kq5obTK1PuXplfzWQIRbdpNKsyuwDcp3KVX9LQafZeMZk84Q0PqounWK2pVJ3xcBHfUPxwizEbRcr2a46o2K73V6pG/Tw+1YYqFdqra5JPAnHK8szpoI7xOUKnunf8hEae7Ayuz4QGt4PsEve3Vo9dfW74hS/uGMm+uauRv+kVe6jGJ/p1DwDsuXUUmhn7biDN1OAyRm462VK+hhY/+XO+6R4Zc8T/Jb7XkQMawbZD9P+ZrL8ucE21Kytra6tTP/g/SDsCiSgsAAA==");
    static final Glyph G9 =
            new Glyph(
                    77,
                    42,
                    1,
                    "H4sIAAAAAAAC/6VXW0yURxQ+uwurBeyiIKEKRLzEREPQULHe8VKh0gfjBUy8VEsrxlDjpVFiGy9obE2IiRpNRNGiPqhp2sS7UcRbRKpiq01Xa1aMSNoqKmjELAtOz8yZmX9+8OVf52XO9/1zvp2Zc+bMLGP21lq7Z9PKhUXfl558yt6vvSof1wV0S9v2InypN+uiwd7iD4Wrda0fKeQdud3SWJVPYHV4WpUxwrvPZYn3Rgq8Oxyt212Fb0SNZgoF0eO56FytRJ4XyH1HDQodnzvI502c8ONjVpOWIcm2j2lZKy35R8RsL0vlXT2RDTt7cbRXLWcQgv6jBgB0yfWAT7L75JZXGpNNFMxnrJp31xW7HUH312RvRHt2AI2bcXzMZDlkohRrNMRGCGYAYz2xO6bYCgQryFyBy99B5mY+dC3ZT92k5Wk1xNIEFcfYeDMQJbixD4VVjvR6yVbxoafJrlaZddcQ8wlmKGMF2G1Q7GSAecIIYMiGv1Xx40FqIvtnJVZsadXKtBOTgcWSfeaFiPvCmoJstRp8FcFgaZcrsa73tNhyYg7SNk2T7FaAQspxJMfpwYcQfSXtX/QJSn4oqYZYgVNCjF3AfqRMoVSI/kdY/Igc0GKrEJVL+w/rPKb6icoluA/NOuz7EnsAYA3VBC/m1kstxmMkHVl7rKXmO8qJbwiIJAh5AKLEuGAf6P1Kr2W01gp+gMmngsGKjFrhWvry35lk5tOAFDSbuVEKIAsJ/7FVWuy8yG7VHkSZpSeS0s69pp2+jkHAI9PQDXKkQxZSFdrdljyM7YBObeRN9XEOoovYz4LoOkl9hNQV9b2RV5xzRop+0lFrl94D9h3Cw4wdw2OvKF5k/lSgmC/Digb7O7WDVlSz9bEM8VbW1BvGqh8I8SEBCfwYWUi3pAoiOq1yqjWzM+JsLICYgKY81syCmT5c5iL5oanQDe9o1o7eRTQfk2GXNVm+Z8dVFT38IcBP8hAmkXPWmRKvKebWJ68FUUY8fG5scQ5Sy4S1GpbcQvC7AFeo+MMCLD81SaZatnZN4DDhP2aPfvQ1xh7PhJy2bQiecLa+J3lSBj7JMtXuK9dhPJdP2K5Yvk5X+lAPTGpheWj/xtnp5DckJMO02BDTFXGGXpRuVXQDuYpwQfxe6Ftay+7IEntDj9ri0mIqQrxAZwQ73Gd/zUqITP6C+wXU2orJ+NQYtVuL5Tm8L3Oh8137pRKb4lBsILk1mNyLblKswKEYbaP3rY0cJcXWOxSLF15JdlK+WuBXh2J0OXrbbOQE+aIKOhRbQn4PbK9HujFhudPXz0Xy+8Hk9hOX9NzxW2qScIw1HOtoYq5K5w8zP7lO1ftTn0n3wMFwnnlXqWoMOSWC0FxCV0uPs+E9QP2y/Hcfn5+dQgfTvagx3Kdxe8UIW4lN/Nb/Xv8A/GVfZ/aLi4hJTs8rvRQKV+V/PFLLoaIMAAA=");
    static final Glyph G10 =
            new Glyph(
                    82,
                    42,
                    1,
                    "H4sIAAAAAAAC/61Xa2xURRQ+u91dEFq3PEyqbsFaGxVsKKgbqm1oQYNITAjENvLH2gYpCgZQKmpToIjyA0FQMQqUYlaCPHxg1BhpxAoCUdoAiSs+QCkVQkuhgKV0H+OcOfO6xV93nT93vu+e893ZM+ecmWXMOfpaNr5W8/Tc2lVfdrD/YVzeNGEA6JG/7kKKeleXDgbnGL4tJcGDuSRTtv1oT+e35QReTkGwKV1I3Pa9xA1+gTe4Fjw6UAj4DmlmtiCGdlEo7lJ0FS1+tXH9sSZ8qz94z5Of9rHOGbmNko3fR4Y1xvAUMW9FivERVPS+RWmIJyrcWqzDnrPiZoAGyW+WZJO17CzBTGG9IXz2aH4mwg8lWOkFGLzo8D9XWx4nhePyxSSp2GkpFgomj7H5+PxD87UcZV0zoRnzu5gmwyI9pFGHlwTT+izFfEENY2wLPvdrvpqj5TRdimmrsnYNmj0mwQEVil8sxaBgxvLQ4XOn5u8HyOgSs889ADfqxe9As5US7FSKi41gi0xPxtrEDim+g+/MK2J2JZvza7XDVjRrlmCTUhx4XBssJCbCWCIASoSPNwGG0BJfwvyNa4f1HPrVBn6sMyD7T0m1Zwo8IsbnWE2Vkk+OAnhDzC5hWNaZH7WAw7ACR0wt50SJmkpws8qEKebjt9NGr8U1nTeKEzheoEAi00gGdyMxj8Dz4nUl7ZBY4liAj0x2TbX6IBbXDg3nWh3HM//SWZmu5Unxth4zkAwbAUpodg4zbo1R3I8O7RqeGGR3MT+lp7cuQW8/QCC24EoIAj9byfKTUVzC4Ugr+d6B68YDh9XLZoR/ywRYIsk6/DmmNlkexzPt5jO+v+B7Sf3uL8QtuIM+GCXrj2FgQsZ/D9q8bQn+mtNPcFC3eZnAXvkFY7FxkHZQkaWcKzA2xeqrUq/Kd92PnmbWyPBzGxlbBvCi5sZxrkijCLqkq3S/ONsL/zGWG0Vcz6us1Q+jezXH0wgKFTgzHD0mqQIOkULJ1/UBW9F7QHtXcPhsz93gb3V+5Q45jxV5MMNqZVOmAwae4p3sUMiWnKy9sWtN513sdSvys7D7UbCTFVCDVf2ZQG03kTsF6FyJLfmb8uZ5DbysShOWYgNavIuz3gp4JP6Etp9BzgUxuf5nLEV9EO6l5nvaTrcLN3AqI3L54q58uLeb4a8TLemYbN4m+Vd7tGK14k6K6tztPD7rtF3hebLYi/Ri4h62LDdoyzJFxX26a5gRmy53cF4vFSrcsmKPblqOg75SKT6qqZEA4/v6n/HJxqLggLw5x9Q+UYLeSbN2R4QypGKVm7sEXSUCSQf5oFRc5kZR5Lpd8TjkNQo+caNIh3Ig7iAnysveNTeKz5HzCcc9l05qWOjqTvYdOdvlRZnAQ9Hl7pr3kPDOtLxP0hI9TS4vjlHyn6Zj1hamsybi+i76A/Wegq/E7nTX0xk29JsU7stRecQMKS2fPIKK2lvdmdKdPrGl0NG8s16Ipv5PJvr+rHDuMF969piyVc2xlKT+BbOaR5p0DQAA");
    static final Glyph N0 =
            new Glyph(
                    19,
                    29,
                    0,
                    "H4sIAAAAAAAC//v/HwLuFOryscsGb/wH5f//18piuuPTt5NhDK5voELFDNY/wYwyBpMfYMZ2BoYTELkf4gytYIYNAxfMjEgGdRD1mYmBF2ZqHgMLiLrNwMDwDCoUx8APol4DhXqhQhoM7mBajoFB7B2YdYyBYT2YUQ5UFg620JghAKL6lSBQbPL//198Gdy/QU1YAhRi2bhEUaD3L8zq/8lAMQaZ7ndwgf/v60BCglfhAl/reG026QPFlN9DRbbJcM7+//+pFFAsGiJSz8hzBESfYAOKHQSxWhgY1kDk2oBCIFedYGIIhhrwW52BgRUYgk4MDBdh1kwBKjvw/z8Dgy7CKSwMDEtAQukI5ykzMGwCCTUhhGwYGO6gCSkz6AFJGYYIuMh1BoZ1QGoOA88rmJAvQxSYjmdwgQTR3wIGb0hk/qlk1lz09NfLNeacDfDAul1sIMAqbtf6HMoHANgg3TonAgAA");
    static final Glyph N1 =
            new Glyph(
                    40,
                    29,
                    0,
                    "H4sIAAAAAAAC/3WTP0hCURTGP6UHGUYZokIRSTnUEBkmNBTtZoNLBg61ZThIREQEgQ1CINmQizhYIBRkBA1tLRUtCuVYEdEQhtJQmaX5uv55f65ez3TO9373vHO/ey/PVyJ9Fd1wWjrNPBX3StRityrku62GcrlOc24BU7yI2mcvqS8pLNMmcFaZagc0RYrzDfzxjTECOCkhrw8xsDcFEKWUsDbH4A7ItGm5UBq0xLONnAsYo4QzMn/L9HUdVtIDm5QyVd2ql95Lkkg3ciEheOKmOD+gpVYWH0+W+yrgYd1fXA0zl/Y7CGeUufrBATGGC0k1fUqngDLD4PgQqP0tAeMsjC/0AAtS2Q9sMblyB7tYPJDuCTYXBhxisQcYSmwuDiyKxQwwz8b4GBAU8t924KgJFwSSQn5BTvy9CTcHk5ivARNNsK8uSLfVDPibcAGYCkL+Sq7yLRu7UynP5a7o5B+/HRpbqnq8OvgkfRSwybkIcb01kM2nVjh4JFuPiTxbZ1ktuG1B+3neKb9mVeRJHJfPDdWwyZSoiUuxKjXMeo2cetgjf0P/BkreWogEAAA=");
    static final Glyph N2 =
            new Glyph(
                    41,
                    29,
                    0,
                    "H4sIAAAAAAAC/22UTUhUURiG3+yaKJojkkKMggxkRpOGSlCj0B8SIRRCWVIugnQVllFEoSKOoGZFBS2aVcyiQjJdWIsghbCpVllDC22lU/iDI5kyU82cvnt+7p/zLe55z8tz7/3Oy3cvY6JmLnu3ZhQ1jCRZqgq7qoVI+rXq17/WP5zC0aUU3FwROoVqx4E4F9dQFdvArXiBj1y9AkLCixXC7+TiB4EC0ZIPWaq3Myh1cMlGAM1crqYhR9mXoDnAKzXbgGdcTtMtP6R9Hrl27m7le0CLcr1I4KD0d6LOxj3fvXgbqJW7Yup2matJYNjKjZdF2GGgT26v0yNP80NX4oSV+7rrO1vdAnyR+4U8Ih8w9rsedesWbrYizNhLoNhwggRqI8ES12DCGvT+T3RtAVpN7wKRcA8sW98bOzYhDzBqeNEOHcwL24I+N8bbBDLWpLXWkeMbLSfSE7WAV0XIAzACG3NnPmYssp3IJpO7ExDrIeC+UJ2bst/pa4hiwITint4Tqx7ODFc9wJDweglUKb7tlYLC2cFFKA0N0vtbCqSLuZ3Kha18vIXP6nUPyRrnE1hi53CLMcBrhqQBQV08cnB4o4Mt5kk91mx59QMnhQK6TdunTmgUfQWBFKAHe+ycHk5ESDcaDfsb8MIODgN7pQwge0HZ9Tjr+F4uAjeVbsYRMYKJNhyPO0CanEml/93YXPYk8md+aF9mV8LB0eTkW7zp9gpXemGt/+eGXwRNjtHMf/mmRcalBAAA");
    static final Glyph N3 =
            new Glyph(
                    150,
                    36,
                    0,
                    "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");
    static final Glyph N4 =
            new Glyph(
                    99,
                    30,
                    0,
                    "H4sIAAAAAAAC/7VWe1BUVRj/LbsgaIsr6jqQREgREq6Ipjhr2wNUBGfH0YlieghUGOWMND6q0RgN6GGh09jkmDbolJmS5RQj5mNwJmaaMl3GnEwLoUJEZVcECZZ1PX33nnPuvdv/9/yx93e+7zvne39nGWNsoO3g5oq8e08y81b2PdEAxgSZmev63YCXmbuygO3mavBHAR3mqtgHZJgcp2eAV83VcDsBOGquipPA6GFzVawGikxORTrwEQvtcTvsnv2c8u+5Q/WVC9IaCYbqnNP8UrJr7QOxWU2Mnd6UO8DYlRPbKj0TfhO8PQkzL+idVj/fGe30Hhbb89Tb7V2zoK6VKump+5IIW67RdMkn8DkXvFMfN7158Kz7pybAzVgwSj2xQVwzFnhYwJvrYmd9N9DXEI/lIyrhPSC9KxVifSGu8wI55M48hdaokgaXYekQfbuLq4CNik/B43cBM8S9ZGM0Rz+koDykgAPSZLqlfFri1o7gqdlQ75WF/DpjT6hqf1WD58F8bpM7A2jlUuvI1xscXkqGQwUHYuANc1oaLG306bUCEwsDCuXGeDrRz7mZwAn2Qcxn7RZkqm4tRtJ1zsoD7CEOfyEDWoRRp7BQ+TRbkRgQpMXAKvrsJilhHltP+KKKupVC9sVQFkocatLqZLwYy9GHZjge2CJwG5Ri6aCc7JVZfxaYQ59lQHyPIO0lFZ0qagAWjbjWaCVy1gaPxDSXt0nsAV4QcHOq4tpC4ME7klsETKG6sAPrJekbCtQtXlNk3TtzQ5qKR4DjAgaoks5L+stAPkdDk7+i32Nk5JfaKUruVAod0f6UpE+B+3kAJgDNky5pskcBl8SNwGSNsRVI4+jNx5RfqvGJmmGhOOBRxir1ImJsrXT7ZyDpScMTkm+ITRlQpjG+BkbxxI9VDL1MHr6iMX1kfhVjyUAtM0SWh6OGPPRoIWV/W2DpFvhanCGfii24qZR0xk4RBnyvMTfRromdgSGwARsyw7JXrOd0J7boHcY2UL6uapwu8NdsZYlMjewTWtQ/CUG2UU2IHth9KuizUTsapthS4EUB/6KGnq5zgqTCx9iRLF4lS4BU4wRXxstMfciwUJqSHGUdJKbBCepSvC8gFSXWGFh2pfc60//gu0JgtsZ6HBjfyy5bgDOStB3xQrJCnXP6ogb7RMh4KaFHDKwU4FD/DPmiLQcWSE4LGbNbuVV37B9H1Lf6uR1GFZTht/kx92GadoPML6cGcwENRTvlbgeQLeDQVB5dcmy1IPXnWGWh/E7X+P/3osxT2yO3txbIZeHCXYYiTH5XEwzYMWaQwxXA00qHNDnw0m3+aricx6Tgh0BexKNF0xtv+C+smtPDFgHPjZTr/ybIyNcMkh8Db6mgGrYaXp3tBXDt77vVWhH7/FXjsboIFT2T1KFeSk/dQ4AtsVpnFWNFhGitxVpzZbi1AHlajllL6ZRR4+ZWd+pSw6OBHyPf3osFsQlL1D/Wu8Y5i30GTllJOFLUV5oSY8+uOi33/wGXC2UimgsAAA==");
    static final Glyph N5 =
            new Glyph(
                    93,
                    29,
                    0,
                    "H4sIAAAAAAAC/52WfUxVZRzHv/eF4BrvazINbI4h1FRUMKkLt2UY+ToG7Sa2kZCtQiqYlnMztNZ1LYa1mXMrVulw1WCWypSM0lZrUL6ylVNZZfkKAorcCxe6PP3O8zznOedc6Q/u88c9v9/5nudznvs8v5fDmBjdtXPio9NKD4yzSY83oY/V3J+mu08KfdznXNg2GOj0YsnNScODXdUaKrF1SKzsdkcOubE13w4JfQPcQW68gdyRya+dVRLNa7gVQGW/7hwBOoQ1kgJfBPCLBH9Yeaecjt2Glo8p+l6XITMCOHsUcN6W9q2MqGZDuWNHnG6/Cmck8Hpa+tfCHC10toT9qavSLkdCJPBzhKjiVmiN4wuz0ktKg7SzUBQJnKUBGTzu1tn3WJUZwFRxuj8DX0UE1+LlT2K/ZPs4TNlEyjM8WHJQTJdr3+182XPf71Ldk5xzXj16+fXMmNmtjJ18K++OGfE5IT5irAq7wl/bk0TSTsaGVqIoQFlh5wm2RaoJQIE0xxtc2Uf8Xe7OVsBtQfTagKdZtdpe02gilvNA08zEhhBfXrA9FpgvxVwgSlj+UpQM0/WqtwbYZkUsAJJewfaJtux5bamp9SqtKFVhGxDmH2lI5EbAgyWj3HJnAT/dvbXYOhF7oE6Tkn4z7pwg/5i0fxURNL4C03vFnSeAuDErop0mFE+A9tfF5R/MJjF9QN0LxQM7pH0GX2oXH9Bs7MGqMMiIC8i9m3041UXhc2U60Z817nqAddJ8b6a2yi4nPLp4Pz9/y/iAptv7w9lbbbF8+zruIfkHdbsKKBTWcCpP58eAdqn1Uzids1J2I4Om7w9jvwPIUrDdsmvv0y7JZvC49nsUmKtrzXT4VsonttWXVAVQo8OOUmmOZVLMqV6xH4gWR5vQrV0KTTtB9brCQtlnLxljDyG8oi4Gzur2h/Tu47rzCzlaEQ1k8Xz+2wabXtx66Oz2mSEtjhUUorU04x8LHJhjBKQTaFJ5LooFqy7j7g4jqdgWyoEbJsahqKIR0XLwWRj8RcNJBw6qzkiPnmasbbbohCXAC1K5RNmbbUJ8E704wHMs2hJuHP62uSOhWzlxWhb9Neuiem+9FIrotRuNSd+7CoZUaiWP/S883QgIxh6g3jI4/6j04nnR40G3igKxTT12/N5HBvV8gFmgkSo/NmQ7MQXqXODT5ao40xmKonTMfZiCys/6RHE45MrVeyc7TfByM7wRsT26vRJrmCVF095V3iwgn4d73k3KjDwWWtaoubscM3osJWPKgJn+HAoDQqrB8qBJWAZsMjyqsdjcd/61RdfZUlrfaGWt1tnXwlJkqSpgvRn+72bHg3uvjN5oWeTaFjILXnMcsespvIGspfazkGr/tDo2fMqnwXI6/fIb4mQ5f6Tsxz5z898wLzEqxeO7Zk2vijLLuy48FZNczFOsMWmql4K0QP8kFPUxGKM+GdEl5/wHOIVYi4kKAAA=");

    static void match(Glyph g) throws Exception {
        assertEquals(
                g.shift,
                OctaveWordShapes.match(g.pixels(), g.width, 0, 0, g.width - 1, g.height - 1));
    }

    @Test
    public void heldOutAltaWords() throws Exception {
        match(G0);
        match(G4);
    }

    @Test
    public void heldOutBassaWords() throws Exception {
        match(G1);
        match(G5);
    }

    @Test
    public void heldOutTwoAltaWords() throws Exception {
        match(G2);
        match(G6);
    }

    @Test
    public void heldOutTwoBassaWords() throws Exception {
        match(G3);
        match(G7);
    }

    @Test
    public void bareEightCannotBecomeAFullWord() throws Exception {
        match(N0);
    }

    @Test
    public void bareFifteenCannotBecomeAFullWord() throws Exception {
        match(N1);
    }

    @Test
    public void ordinaryNumbersAndWordsCannotBecomeDirections() throws Exception {
        match(N2);
        match(N3);
        match(N4);
        match(N5);
    }

    @Test
    public void tightlyTypesetFifteenAltaIsRecognized() throws Exception {
        match(G8);
    }

    @Test
    public void tightlyTypesetAltaWithSmallSuffixIsRecognized() throws Exception {
        match(G9);
    }

    @Test
    public void tightlyTypesetAltaWithLargerSuffixIsRecognized() throws Exception {
        match(G10);
    }

    static ScoreNoteEvent note(float x, int step, int y, boolean tied) {
        return new ScoreNoteEvent(
                0,
                x,
                step,
                0,
                1,
                y / 400f,
                tied,
                0,
                0,
                2,
                1,
                1,
                0,
                0,
                18,
                false,
                0,
                false,
                0,
                0,
                1,
                0,
                ScoreNoteEvent.Kind.PITCHED);
    }

    @Test
    public void acquiredDirectionOwnsChordAndCarriesItsTiedChord() throws Exception {
        int w = 600, h = 400;
        byte[] g = new byte[w * h];
        Arrays.fill(g, (byte) 255);
        byte[] pixels = G9.pixels();
        for (int y = 0; y < G9.height; y++)
            System.arraycopy(pixels, y * G9.width, g, (40 + y) * w + 60, G9.width);
        var notes =
                List.of(
                        note(.2f, 2, 180, false),
                        note(.2f, 4, 168, false),
                        note(.3f, 2, 180, true),
                        note(.3f, 4, 168, true),
                        note(.6f, 2, 180, false));
        byte[] before = g.clone();
        var result =
                OctaveMarkDetector.apply(
                        List.of(),
                        List.of(new PlayingTechniqueDetector.Staff(130, 226, 24, 0, 1)),
                        List.of(new MeasureRegion(0, 1, 0, 1)),
                        notes,
                        g,
                        w,
                        h);
        for (int i = 0; i < notes.size(); i++) {
            assertEquals(i < 4 ? 1 : 0, result.get(i).octaveShift());
            assertEquals(notes.get(i), result.get(i).withOctaveShift(0));
        }
        assertArrayEquals(before, g);
    }
}
