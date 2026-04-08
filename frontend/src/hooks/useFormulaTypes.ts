import { useEffect, useState } from 'react';
import axios from 'axios';
import { useServerStore } from '../stores/serverStore';

export interface FormulaType {
  type_name: string;
  display_name: string;
  description: string;
  sample_prompts: string[];
}

export function useFormulaTypes() {
  const [formulaTypes, setFormulaTypes] = useState<FormulaType[]>([]);
  const { current } = useServerStore();

  useEffect(() => {
    const url = `${current.baseUrl}${current.apiPrefix}/formula-types`;
    const headers: Record<string, string> = {};
    if (current.auth) {
      headers['Authorization'] = `Basic ${btoa(`${current.auth.username}:${current.auth.password}`)}`;
    }
    axios
      .get<FormulaType[]>(url, { headers })
      .then((res) => setFormulaTypes(res.data))
      .catch((err) => {
        console.warn('Failed to load formula types from API, using fallback:', err.message);
        setFormulaTypes([
          { type_name: 'WORKFORCE_MANAGEMENT_TIME_CALCULATION_RULES', display_name: 'Time Calculation Rule', description: '', sample_prompts: [
            'Divides reported daily or period time into calculated time attributes for hours above and below defined threshold hours',
            'Daily overtime calculation using event database items and assignment values',
            'Defines shift hours between a start and end time that receive an additional time attribute',
            'Evaluates whether time entered is on public holiday defined in the assigned schedule',
            'Divides reported daily, period, and seventh-consecutive-day time entries into overtime tiers',
            'Evaluates whether the time between reported consecutive shifts is less than the defined rest period and adds premium',
            'Subtracts the time not worked entry for the scheduled shift from a reported time entry calculated duration',
            'Apply cost overrides to calculated time entries',
          ]},
          { type_name: 'WORKFORCE_MANAGEMENT_TIME_ENTRY_RULES', display_name: 'Time Entry Rule', description: '', sample_prompts: [
            'Evaluates whether reported time entries for the time category are greater than a defined maximum hours value',
            'Evaluates whether reported time entries for the time category are less than a defined minimum hours value',
            'Evaluates whether the reported time entries are within the scheduled hours plus or minus the defined variance',
            'Evaluates whether reported time entries are on an assigned public holiday',
            'Evaluates whether the time between reported consecutive shifts is less than the defined rest period',
            'Determines if the selected eligible job is valid for all time entries',
          ]},
          { type_name: 'WORKFORCE_MANAGEMENT_TIME_SUBMISSION_RULES', display_name: 'Time Submission Rule', description: '', sample_prompts: [
            'Submits or saves the time card when the device Out event is reported on a specific day of the week',
            'Submits or saves the time card when the reported hours exceeds the defined minimum hours',
            'Submits or saves the time card when the device Out events exceed the defined minimum entries value',
          ]},
          { type_name: 'WORKFORCE_MANAGEMENT_TIME_COMPLIANCE_RULES', display_name: 'Time Compliance Rule', description: '', sample_prompts: [
            'Evaluate whether a time card exists for the worker in the current period',
            'Evaluate whether time entries exist for the current day if the worker is scheduled to work',
            'Checks the worker or manager time card approval status and sends a reminder notification',
          ]},
          { type_name: 'WORKFORCE_MANAGEMENT_TIME_DEVICE_RULES', display_name: 'Time Device Rule', description: '', sample_prompts: [
            'Evaluates whether time entries reported using the time collection device are before or after the scheduled start and end times',
            'Evaluates whether reported shifts are less than the defined minimum rest period minutes apart',
          ]},
          { type_name: 'WORKFORCE_MANAGEMENT_TIME_ADVANCE_CATEGORY_RULES', display_name: 'Time Advanced Category Rule', description: '', sample_prompts: [
            'Checks if the worker is scheduled to work today',
            'Calculates the work duration of a shift and the expected number of meal breaks',
          ]},
          { type_name: 'WORKFORCE_MANAGEMENT_SUBROUTINE', display_name: 'WFM Subroutine', description: '', sample_prompts: [
            'Divides reported daily or period time into calculated time attributes for hours above and below defined threshold hours',
            'Subroutine for Time Allocation formula that handles Allocation Type of Hours',
          ]},
          { type_name: 'WORKFORCE_MANAGEMENT_UTILITY', display_name: 'WFM Utility', description: '', sample_prompts: [
            'Calculates duration based on start and stop time',
            'Helper utility for Data Access View Entry (DAVE) time scan',
          ]},
          { type_name: 'Oracle Payroll', display_name: 'Oracle Payroll', description: '', sample_prompts: [
            'Calculates pension deduction',
            'This formula calculates federal income tax',
            'Formula for Flat Amount Earnings Template',
            'Calculate Workers Compensation for an assignment',
            'Social insurance processing formula',
          ]},
        ]);
      });
  }, []);

  return { formulaTypes };
}
