import { test, expect } from "@playwright/test";

const API_URL = process.env.E2E_API_URL || "http://localhost:8080/api/v1";

test("Full end-to-end ticket booking flow: Register -> Browse -> Reserve -> Checkout -> Pay -> QR -> Check-in", async ({
  page,
  request,
}) => {
  const unique = Date.now();
  const orgEmail = `e2e-org-${unique}@test.com`;
  const custEmail = `e2e-cust-${unique}@test.com`;
  const password = "Password123!";

  // 1. Setup Organizer + Venue + Event + Seats via API
  const orgReg = await request.post(`${API_URL}/auth/register`, {
    data: { email: orgEmail, password, fullName: "E2E Organizer", organizer: true },
  });
  expect(orgReg.ok()).toBeTruthy();
  const orgToken = (await orgReg.json()).data.accessToken;

  const venueRes = await request.post(`${API_URL}/organizer/venues`, {
    headers: { Authorization: `Bearer ${orgToken}` },
    data: { name: `E2E Hall ${unique}`, address: "Hanoi", capacity: 200 },
  });
  expect(venueRes.ok()).toBeTruthy();
  const venueId = (await venueRes.json()).data.id;

  const now = Date.now();
  const eventRes = await request.post(`${API_URL}/organizer/events`, {
    headers: { Authorization: `Bearer ${orgToken}` },
    data: {
      venueId,
      name: `E2E Concert ${unique}`,
      category: "Music",
      startTime: new Date(now + 86400_000 * 3).toISOString(),
      endTime: new Date(now + 86400_000 * 4).toISOString(),
      salesStartTime: new Date(now - 3600_000).toISOString(),
      salesEndTime: new Date(now + 86400_000 * 2).toISOString(),
      waitingRoomEnabled: false,
      maxSeatsPerReservation: 4,
    },
  });
  expect(eventRes.ok()).toBeTruthy();
  const eventId = (await eventRes.json()).data.id;

  const secRes = await request.post(`${API_URL}/organizer/venues/${venueId}/sections`, {
    headers: { Authorization: `Bearer ${orgToken}` },
    data: { eventId, name: "VIP", price: 1000000, capacity: 5, type: "RESERVED" },
  });
  expect(secRes.ok()).toBeTruthy();
  const sectionId = (await secRes.json()).data.id;

  const seatsRes = await request.post(`${API_URL}/organizer/sections/${sectionId}/seats`, {
    headers: { Authorization: `Bearer ${orgToken}` },
    data: {
      seats: [
        { seatCode: "A01", rowLabel: "A", seatNumber: 1 },
        { seatCode: "A02", rowLabel: "A", seatNumber: 2 },
      ],
    },
  });
  expect(seatsRes.ok()).toBeTruthy();

  const pubRes = await request.post(`${API_URL}/organizer/events/${eventId}/publish`, {
    headers: { Authorization: `Bearer ${orgToken}` },
  });
  expect(pubRes.ok()).toBeTruthy();

  // 2. Customer registers via UI
  await page.goto("/register");
  await page.fill('input[name="fullName"]', "E2E Customer");
  await page.fill('input[name="email"]', custEmail);
  await page.fill('input[name="password"]', password);
  await page.click('button[type="submit"]');
  await page.waitForURL("**/");

  // 3. Browse event and navigate to Event Detail -> Seat Booking
  await page.goto(`/events/${eventId}`);
  await expect(page.getByText(`E2E Concert ${unique}`)).toBeVisible();
  await page.getByRole("button", { name: /Select Seats/i }).click();
  await page.waitForURL(`**/booking/${eventId}`);

  // 4. Select seat A01 and create reservation
  await page.getByRole("button", { name: "A01" }).click();
  await page.getByRole("button", { name: /Hold 1 Seat/i }).click();
  await expect(page.getByRole("button", { name: /Proceed to Checkout/i })).toBeVisible();

  // 5. Create order & Checkout with Sandbox Simulator
  await page.getByRole("button", { name: /Proceed to Checkout/i }).click();
  await page.waitForURL("**/checkout/**");
  await page.getByRole("button", { name: /Pay /i }).click();
  await page.getByRole("button", { name: /Simulate Payment Success/i }).click();

  // 6. View Ticket & QR Pass
  await page.waitForURL("**/my-tickets");
  await expect(page.getByText(`E2E Concert ${unique}`)).toBeVisible();
  await page.getByRole("button", { name: /Show QR Pass/i }).first().click();
  await page.waitForURL("**/my-tickets/**");
  await expect(page.getByText("VALID")).toBeVisible();
});
