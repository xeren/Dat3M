#include <pthread.h>
#include "mutex.h"
#include <assert.h>

#ifndef NTHREADS
#define NTHREADS 3
#endif

int shared;
mutex_t mutex;
int sum = 0;

void *thread_n(void *arg)
{
    intptr_t index = ((intptr_t) arg);

    mutex_lock(&mutex);
    shared = index;
    int r = shared;
    assert(r == index);
    sum++;
    mutex_unlock(&mutex);
    return NULL;
}

int main()
{
    pthread_t t[NTHREADS];

    mutex_init(&mutex);

    pthread_create(&t[0], 0, thread_n, (void *)(size_t)0);
    pthread_create(&t[1], 0, thread_n, (void *)(size_t)1);
    pthread_create(&t[2], 0, thread_n, (void *)(size_t)2);

    pthread_join(t[0], 0);
    pthread_join(t[1], 0);
    pthread_join(t[2], 0);

    assert(sum == 3);

    return 0;
}
