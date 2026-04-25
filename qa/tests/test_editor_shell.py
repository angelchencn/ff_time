from __future__ import annotations

from selenium.webdriver.common.by import By
from selenium.webdriver.support import expected_conditions as EC
from selenium.webdriver.support.ui import WebDriverWait

BASE_URL = "http://127.0.0.1:4173"


def test_editor_workspace_loads_and_navigates_to_templates(driver):
    driver.get(BASE_URL)
    wait = WebDriverWait(driver, 10)

    wait.until(EC.visibility_of_element_located((By.TAG_NAME, "h1")))
    assert driver.find_element(By.TAG_NAME, "h1").text == "Fast Formula"
    assert driver.find_element(By.CSS_SELECTOR, "[data-testid='ff-editor']").is_displayed()

    validation = driver.find_element(By.CSS_SELECTOR, "[data-testid='validation-results']")
    assert "VALID" in validation.text
    assert "No issues found." in validation.text

    driver.find_element(By.CSS_SELECTOR, "[data-testid='toolbar-templates-button']").click()

    wait.until(EC.text_to_be_present_in_element((By.TAG_NAME, "h1"), "Manage Templates"))
    templates_list = driver.find_element(By.CSS_SELECTOR, "[data-testid='templates-list']")
    assert "Shift Differential" in templates_list.text

    driver.find_element(By.CSS_SELECTOR, "[data-testid='templates-back-button']").click()
    wait.until(EC.text_to_be_present_in_element((By.TAG_NAME, "h1"), "Fast Formula"))


def test_validation_panel_turns_invalid_after_bad_formula(driver):
    driver.get(BASE_URL)
    wait = WebDriverWait(driver, 10)

    wait.until(lambda d: d.execute_script("return typeof window.__ffTimeQaSetEditorValue === 'function'"))
    driver.execute_script("window.__ffTimeQaSetEditorValue(arguments[0])", "BAD_TOKEN")

    wait.until(
        EC.text_to_be_present_in_element(
            (By.CSS_SELECTOR, "[data-testid='status-validation-state']"),
            "ERROR",
        )
    )

    validation = driver.find_element(By.CSS_SELECTOR, "[data-testid='validation-results']")
    assert "INVALID" in validation.text
    assert "Unexpected token BAD_TOKEN" in validation.text
