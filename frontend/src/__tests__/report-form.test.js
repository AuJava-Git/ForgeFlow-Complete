import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'
import ReportForm from '../components/ReportForm.vue'

describe('MES production report form', () => {
  it('rejects zero-zero quantities and emits a valid report', async () => {
    const wrapper = mount(ReportForm)
    const good = wrapper.get('[data-test="good-quantity"]')
    const defect = wrapper.get('[data-test="defect-quantity"]')

    await good.setValue('0')
    await defect.setValue('0')
    expect(wrapper.get('[data-test="submit-report"]').element.disabled).toBe(true)

    await good.setValue('3')
    await wrapper.get('[data-test="remark"]').setValue('首件已确认')
    expect(wrapper.get('[data-test="submit-report"]').element.disabled).toBe(false)
    await wrapper.get('[data-test="submit-report"]').trigger('click')

    expect(wrapper.emitted('submit')).toEqual([[{
      goodQuantity: 3,
      defectQuantity: 0,
      remark: '首件已确认',
    }]])
  })

  it('rejects a report that exceeds the remaining plan', async () => {
    const wrapper = mount(ReportForm, { props: { remaining: 5 } })
    await wrapper.get('[data-test="good-quantity"]').setValue('3')
    await wrapper.get('[data-test="defect-quantity"]').setValue('3')

    expect(wrapper.get('[data-test="submit-report"]').element.disabled).toBe(true)
    expect(wrapper.get('[data-test="quantity-error"]').text()).toContain('剩余计划')
  })

  it('rejects negative and fractional quantities', async () => {
    const wrapper = mount(ReportForm)
    await wrapper.get('[data-test="good-quantity"]').setValue('-1')
    await wrapper.get('[data-test="defect-quantity"]').setValue('0')
    expect(wrapper.get('[data-test="submit-report"]').element.disabled).toBe(true)

    await wrapper.get('[data-test="good-quantity"]').setValue('1.5')
    expect(wrapper.get('[data-test="submit-report"]').element.disabled).toBe(true)
  })
})
