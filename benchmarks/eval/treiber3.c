#include "treiber.h"
#include <pthread.h>
#include <assert.h>

#ifndef NTHREADS
#define NTHREADS 3
#endif

void *worker(void *arg)
{

    intptr_t index = ((intptr_t) arg);

    push(index);
    int r = pop();

    assert(r != EMPTY);

    return NULL;
}

int main()
{
    pthread_t t[NTHREADS];

    init();

    pthread_create(&t[0], 0, worker, (void *)(size_t)0);
    pthread_create(&t[1], 0, worker, (void *)(size_t)1);
    pthread_create(&t[2], 0, worker, (void *)(size_t)2);

    pthread_join(t[0], 0);
    pthread_join(t[1], 0);
    pthread_join(t[2], 0);

    int r = pop();
    assert(r == EMPTY);

    return 0;
}
