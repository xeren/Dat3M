#include "dglm.h"
#include <pthread.h>

void *worker(void *arg)
{

    intptr_t index = ((intptr_t) arg);

	enqueue(index);
    int r = dequeue();

	assert(r != EMPTY);

	return NULL;
}

int main()
{
    pthread_t t[2];

    init();

    pthread_create(&t[0], 0, worker, (void *)(size_t)0);
    pthread_create(&t[1], 0, worker, (void *)(size_t)1);

    pthread_join(t[0], 0);
    pthread_join(t[1], 0);

    int r = dequeue();
    assert(r == EMPTY);

    return 0;
}
