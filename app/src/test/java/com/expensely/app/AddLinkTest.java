package com.expensely.app;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import org.junit.Test;

public class AddLinkTest {

    private static final String BASE = "https://expensely-app.netlify.app/expenses?add=1";

    private static String web(String url) {
        AddLink link = AddLink.parse(url);
        assertNotNull(url, link);
        return link.webUrl();
    }

    @Test
    public void mapsTheAgreedLinkToTheWebForm() {
        assertEquals(
                BASE + "&description=Nainital%20trip&amount=2500&date=2026-10-08",
                web("expensely://add?description=Nainital%20trip&amount=2500&date=2026-10-08"));
    }

    @Test
    public void everyFieldIsOptional() {
        assertEquals(BASE, web("expensely://add"));
        assertEquals(BASE, web("expensely://add?"));
        assertEquals(BASE + "&amount=10", web("expensely://add?amount=10"));
        assertEquals(BASE + "&description=Tea", web("expensely://add?description=Tea"));
        assertEquals(BASE + "&date=2025-01-31", web("expensely://add?date=2025-01-31"));
    }

    @Test
    public void onlyKnownFieldsAreKept() {
        assertEquals(
                BASE + "&description=x",
                web("expensely://add?description=x&category=Food&paid=true&token=abc&userId=1&add=0"));
    }

    @Test
    public void aBadFieldIsDroppedWithoutLosingTheGoodOnes() {
        assertEquals(BASE + "&description=Hi", web("expensely://add?description=Hi&amount=-5&date=2026-02-30"));
    }

    @Test
    public void amountMustBeAboveZeroWithAtMostTwoDecimals() {
        assertEquals("2500.5", AddLink.cleanAmount("2500.5"));
        assertEquals("3700.17", AddLink.cleanAmount("3700.17"));
        assertEquals("0.01", AddLink.cleanAmount("0.01"));
        for (String bad :
                new String[] {
                    "0", "0.0", "0.00", "-5", "", "abc", "1e3", "1.234", ".5", "5.", "1,000", "12abc", "NaN",
                    "Infinity", "1000000000"
                }) {
            assertNull("amount " + bad, AddLink.cleanAmount(bad));
        }
    }

    @Test
    public void dateMustBeARealCalendarDate() {
        assertEquals("2026-10-08", AddLink.cleanDate("2026-10-08"));
        assertEquals("2024-02-29", AddLink.cleanDate("2024-02-29"));
        assertEquals("2000-02-29", AddLink.cleanDate("2000-02-29"));
        assertEquals("2026-12-31", AddLink.cleanDate("2026-12-31"));
        for (String bad :
                new String[] {
                    "2025-02-29", "2100-02-29", "2026-13-01", "2026-00-10", "2026-10-00", "2026-04-31", "2026-02-30",
                    "08-10-2026", "2026/10/08", "2026-1-8", "today", "", "2026-10-08T10:00"
                }) {
            assertNull("date " + bad, AddLink.cleanDate(bad));
        }
    }

    @Test
    public void descriptionIsTrimmedAndCutToOneHundredCharacters() {
        assertEquals("hi", AddLink.cleanDescription("  hi  "));
        assertNull(AddLink.cleanDescription("   "));
        StringBuilder long150 = new StringBuilder();
        for (int i = 0; i < 150; i++) {
            long150.append('x');
        }
        assertEquals(100, AddLink.cleanDescription(long150.toString()).length());
    }

    @Test
    public void emojiAreNeverCutInHalf() {
        StringBuilder pizzas = new StringBuilder();
        for (int i = 0; i < 150; i++) {
            pizzas.append("🍕");
        }
        String cut = AddLink.cleanDescription(pizzas.toString());
        assertEquals(100, cut.codePointCount(0, cut.length()));
        assertEquals(cut, AddLink.parse("expensely://add?description=" + AddLink.encode(cut)).description());
    }

    @Test
    public void specialCharactersSurviveEncoding() {
        String text = "Tea & snacks = 100% #fun? ₹";
        assertEquals(text, AddLink.parse("expensely://add?description=" + AddLink.encode(text)).description());
        assertEquals(
                BASE + "&description=Tea%20%26%20snacks%20%3D%20100%25%20%23fun%3F%20%E2%82%B9",
                web("expensely://add?description=" + AddLink.encode(text)));
    }

    @Test
    public void aValueCannotSmuggleExtraParameters() {
        AddLink link = AddLink.parse("expensely://add?description=a%26amount%3D999");
        assertEquals("a&amount=999", link.description());
        assertNull(link.amount());
        assertEquals(BASE + "&description=a%26amount%3D999", link.webUrl());
    }

    @Test
    public void firstValueWinsAndABrokenEscapeDropsOnlyThatField() {
        assertEquals("5", AddLink.parse("expensely://add?amount=5&amount=9").amount());
        AddLink link = AddLink.parse("expensely://add?description=%E0%A4%A&amount=7");
        assertNull(link.description());
        assertEquals("7", link.amount());
    }

    @Test
    public void rejectsLinksThatAreNotExpenselyAdd() {
        assertNull(AddLink.parse(null));
        assertNull(AddLink.parse(""));
        assertNull(AddLink.parse("https://expensely-app.netlify.app/expenses?add=1"));
        assertNull(AddLink.parse("expensely://login?amount=5"));
        assertNull(AddLink.parse("expensely://addition?amount=5"));
        assertNull(AddLink.parse("expensely://evil.com/add?amount=5"));
        assertNull(AddLink.parse("javascript:alert(1)"));
    }

    @Test
    public void schemeAndHostAreCaseInsensitive() {
        assertEquals(BASE + "&amount=5", web("EXPENSELY://ADD?amount=5"));
        assertEquals(BASE + "&amount=5", web("expensely://add/?amount=5"));
        assertEquals(BASE + "&amount=5", web("expensely://add?amount=5#frag"));
    }
}
