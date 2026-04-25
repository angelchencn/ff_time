from __future__ import annotations

from selenium.webdriver.common.by import By
from selenium.webdriver.support import expected_conditions as EC
from selenium.webdriver.support.ui import WebDriverWait

BASE_URL = "http://127.0.0.1:4173"


def open_templates_page(driver):
    driver.get(BASE_URL)
    wait = WebDriverWait(driver, 10)
    driver.find_element(By.CSS_SELECTOR, "[data-testid='toolbar-templates-button']").click()
    wait.until(EC.text_to_be_present_in_element((By.TAG_NAME, "h1"), "Manage Templates"))
    return wait


def test_new_template_creates_dirty_draft(driver):
    open_templates_page(driver)

    driver.find_element(By.CSS_SELECTOR, "[data-testid='templates-new-button']").click()

    name_input = driver.find_element(By.CSS_SELECTOR, "[data-testid='template-name-input']")
    assert name_input.get_attribute("value") == "New Template"

    save_button = driver.find_element(By.CSS_SELECTOR, "[data-testid='template-save-button']")
    assert "Save changes" in save_button.text

    templates_list = driver.find_element(By.CSS_SELECTOR, "[data-testid='templates-list']")
    assert "New Template" in templates_list.text


def test_new_template_can_be_saved(driver):
    wait = open_templates_page(driver)

    driver.find_element(By.CSS_SELECTOR, "[data-testid='templates-new-button']").click()

    name_input = driver.find_element(By.CSS_SELECTOR, "[data-testid='template-name-input']")
    name_input.clear()
    name_input.send_keys("QA Saved Template")

    description = driver.find_element(By.CSS_SELECTOR, "[data-testid='template-description-input']")
    description.clear()
    description.send_keys("Created and saved from QA smoke test.")

    save_button = driver.find_element(By.CSS_SELECTOR, "[data-testid='template-save-button']")
    save_button.click()

    wait.until(
        EC.text_to_be_present_in_element(
            (By.CSS_SELECTOR, "[data-testid='template-save-button']"),
            "Saved",
        )
    )

    templates_list = driver.find_element(By.CSS_SELECTOR, "[data-testid='templates-list']")
    assert "QA Saved Template" in templates_list.text


def test_edit_description_and_save(driver):
    wait = open_templates_page(driver)

    wait.until(
        EC.element_to_be_clickable((By.XPATH, "//span[normalize-space()='Shift Differential']"))
    ).click()

    description = driver.find_element(By.CSS_SELECTOR, "[data-testid='template-description-input']")
    description.clear()
    description.send_keys("Updated description from QA smoke test.")

    save_button = driver.find_element(By.CSS_SELECTOR, "[data-testid='template-save-button']")
    save_button.click()

    wait.until(
        EC.text_to_be_present_in_element(
            (By.CSS_SELECTOR, "[data-testid='template-save-button']"),
            "Saved",
        )
    )


def test_delete_existing_template(driver):
    wait = open_templates_page(driver)

    wait.until(
        EC.element_to_be_clickable((By.XPATH, "//span[normalize-space()='Holiday Pay']"))
    ).click()

    delete_button = driver.find_element(
        By.XPATH,
        "//button[.//span[@role='img' and @aria-label='delete']]",
    )
    delete_button.click()

    wait.until(
        EC.visibility_of_element_located(
            (By.CSS_SELECTOR, ".ant-modal-confirm-title")
        )
    )
    driver.find_element(By.XPATH, "//button[normalize-space()='OK']").click()

    wait.until_not(
        EC.text_to_be_present_in_element(
            (By.CSS_SELECTOR, "[data-testid='templates-list']"),
            "Holiday Pay",
        )
    )

    templates_list = driver.find_element(By.CSS_SELECTOR, "[data-testid='templates-list']")
    assert "Holiday Pay" not in templates_list.text
