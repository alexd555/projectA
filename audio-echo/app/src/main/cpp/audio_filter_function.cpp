

#include "audio_filter_function.h"
#include <limits.h>

#define BYTES_PER_SAMPLE 2

#define FILTER_LEN 49

static double filter[FILTER_LEN]={
        -0.0131225585937500,0.0264282226562500,0.000976562500000000,-0.00924682617187500,-0.0111999511718750,-0.00781250000000000,-0.000885009765625000,0.00720214843750000,0.0130310058593750,0.0132141113281250,0.00640869140625000,-0.00561523437500000,-0.0177001953125000,-0.0233764648437500,-0.0177612304687500,-0.000610351562500000,0.0225524902343750,0.0411376953125000,0.0434570312500000,0.0212707519531250,-0.0261535644531250,-0.0903320312500000,-0.155731201171875,-0.204498291015625,0.777465820312500,-0.204498291015625,-0.155731201171875,-0.0903320312500000,-0.0261535644531250,0.0212707519531250,0.0434570312500000,0.0411376953125000,0.0225524902343750,-0.000610351562500000,-0.0177612304687500,-0.0233764648437500,-0.0177001953125000,-0.00561523437500000,0.00640869140625000,0.0132141113281250,0.0130310058593750,0.00720214843750000,-0.000885009765625000,-0.00781250000000000,-0.0111999511718750,-0.00924682617187500,0.000976562500000000,0.0264282226562500,-0.0131225585937500
};




inline int16_t  clampAudioSample(double sample) {
  return (int16_t) (sample > SHRT_MAX ? SHRT_MAX :
            (sample < SHRT_MIN ? SHRT_MIN : (int16_t)(sample + 0.5f)));
}
static int16_t residue[FILTER_LEN];
static int16_t* cacheBuffer = nullptr;
int16_t* convolution(int16_t *samples,int sampleCount, double *filter, int filterLen)
{
  assert(sampleCount >= filterLen);

  int32_t idx = 0;
  for (idx = 0; idx < (filterLen - 1); idx++) {
    double filterSample = static_cast<double>(0.0f);
    int32_t tapIdx = 0;
    for (; tapIdx < filterLen - 1 - idx; tapIdx++) {
      filterSample += residue[idx + tapIdx] * filter[tapIdx];
    }
    int32_t curTapIdx  = tapIdx;
    for (; tapIdx < filterLen; tapIdx++) {
      filterSample += samples[tapIdx - curTapIdx] * filter[tapIdx];
    }
    cacheBuffer[idx] = clampAudioSample(filterSample);
  }

  for(idx = filterLen -1; idx < sampleCount; idx++) {
    double filterSample = static_cast<double>(0.0f);
    for (uint32_t tapIdx = 0; tapIdx < filterLen; tapIdx++) {
      filterSample += samples[idx - tapIdx] * filter[tapIdx];
    }
    cacheBuffer[idx] = clampAudioSample(filterSample);
  }
  // save residues back
  memcpy(residue, &samples[sampleCount - (filterLen - 1)],
         (filterLen - 1 ) * BYTES_PER_SAMPLE);

  int16_t *retBuf = cacheBuffer;
  cacheBuffer = samples;
  return retBuf;
}

/*
 * Note: this function is pass-through right now. The real implementation
 * must have some bug in it: it crashes the app.
 */
void FilterFunc_FilterFrame(sample_buf *container) {

  container->buf_ = (uint8_t*)convolution((int16_t*)container->buf_,
                                           container->size_/BYTES_PER_SAMPLE,
                                           filter, FILTER_LEN);
  (void) filter;
}


bool FilterFunc_Init(void *buf) {
  cacheBuffer = (int16_t*)buf;
  memset(residue, 0, sizeof(residue));
  return true;
}

void* FilterFunc_Fini(void) {
    void *buf = cacheBuffer;
    cacheBuffer = nullptr;
    return buf;
}