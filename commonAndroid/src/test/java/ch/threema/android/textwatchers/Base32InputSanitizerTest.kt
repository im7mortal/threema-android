package ch.threema.android.textwatchers

import android.text.Editable
import io.mockk.every
import io.mockk.mockk
import kotlin.test.Test
import kotlin.test.assertEquals

class Base32InputSanitizerTest {
    @Test
    fun `empty input`() {
        testInputSanitization(
            null,
            "",
        )
    }

    @Test
    fun `valid base32 input`() {
        // backup id string of 129 characters
        testInputSanitization(
            null,
            "AHCV-YVN5-MZF6-H47E-BFDA-XPQ4-523T-QEJ7-Q7TB-5O2G-U3LM-IPAY-PMO3-RWYJ-FZ5F-VRAH-5MHT-IP2E-ODYI-GXW4-4NAF-EOCO-OZPR-NZK4-CHVE-LYVL",
        )
        // legacy backup id string of 99 characters
        testInputSanitization(
            null,
            "4K4M-5Q6T-KFUH-KHL5-2VCJ-ZM57-NL7R-WJTA-V45L-NJAM-WLEU-5DS4-XF7S-OPH4-CTCL-N2CF-3C4C-HPB7-YZWW-U3S6",
        )
    }

    @Test
    fun `partial base32 input`() {
        testInputSanitization(
            null,
            "4K4",
        )
        testInputSanitization(
            null,
            "4K4M-5",
        )
        testInputSanitization(
            null,
            "4K4M-5Q6T-KFUH-KHL5-2VCJ-ZM57-NL7R-",
        )
    }

    @Test
    fun `invalid characters get stripped and dashes get placed in correct positions`() {
        testInputSanitization(
            "4K4M-5Q6T-KFUH-KHL5-2VCJ-ZM57-NL7R-WJTA-V45L-NJAM-WLEU-5DS4-XF7S-OPH4-CTCL-N2CF-3C4C-HPB7-YZWW-U3S6",
            " 4K4M-5Q6T-KFUH-KHL5-2VCJ-ZM57-NL7R-äWJTA-V45L-NJAM-\nWLEU-5éDS4--XF7S-OPH4CTCL-N2CF- 3C4C-HPB7-YZ%WW-U3S6\n",
        )
        testInputSanitization(
            "4K4M-5Q6T-KFUH-KHL5-2VCJ-ZM57-NL7R-WJTA-V45L-NJAM-WLEU-5DS4-XF7S-OPH4-CTCL-N2CF-3C4C-HPB7-YZWW-U3S6",
            "4K4M5Q6TKFUHKHL52VCJZM57NL7RWJTAV45LNJAMWLEU5DS4XF7SOPH4CTCLN2CF3C4CHPB7YZWWU3S6",
        )
    }

    @Test
    fun `at most one trailing dash is allowed`() {
        testInputSanitization(
            "4K4M-",
            "--4K4M--",
        )
    }

    private fun testInputSanitization(expectedOutput: String?, input: String) {
        var actualOutput: String? = null
        val editableMock = mockk<Editable>(input) {
            every { this@mockk.toString() } returns input
            every { length } returns input.length
            every { replace(0, input.length, any()) } answers {
                actualOutput = thirdArg<String>()
                mockk()
            }
        }
        Base32InputSanitizer().afterTextChanged(editableMock)
        assertEquals(expectedOutput, actualOutput)
    }
}
