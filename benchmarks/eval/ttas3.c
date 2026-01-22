#include <pthread.h>
#include "ttas.h"
#include <assert.h>

int shared;
ttaslock_t lock;
int sum = 0;

void *thread_n(void *arg)
{
    intptr_t index = ((intptr_t) arg);

    ttaslock_acquire(&lock);
    shared = index;
    int r = shared;
    assert(r == index);
    sum++;
    ttaslock_release(&lock);
    return NULL;
}

int main()
{
    pthread_t t[3];

    ttaslock_init(&lock);

    pthread_create(&t[0], 0, thread_n, (void *)(size_t)0);
    pthread_create(&t[1], 0, thread_n, (void *)(size_t)1);
    pthread_create(&t[2], 0, thread_n, (void *)(size_t)2);

    pthread_join(t[0], 0);
    pthread_join(t[1], 0);
    pthread_join(t[2], 0);

    assert(sum == 3);

    return 0;
}
